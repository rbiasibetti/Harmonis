package app.harmonis.auth;

import app.harmonis.identity.AclCatalog;
import app.harmonis.identity.User;
import app.harmonis.identity.UserDto;
import app.harmonis.identity.UserRepository;
import app.harmonis.security.JwtService;
import app.harmonis.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Sessao por cookies HttpOnly: login emite access JWT (curto) + refresh token (rotativo);
 * /refresh renova; /logout encerra. Nenhum token trafega no corpo da resposta.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final AuthCookies cookies;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt,
                          RefreshTokenService refreshTokens, AuthCookies cookies) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
    }

    public record LoginRequest(
            @NotBlank String tenant,
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record SessionResponse(UserDto user) {}

    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest req,
                                                 HttpServletRequest http, HttpServletResponse res) {
        // Define o tenant ANTES de qualquer acesso a dados: o datasource roteia por aqui.
        TenantContext.set(req.tenant());

        User user = users.findByEmailIgnoreCase(req.email())
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas."));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas.");
        }

        refreshTokens.purgeExpired();
        RefreshTokenService.Issued refresh = refreshTokens.start(user.getId(), req.tenant(), http.getHeader("User-Agent"));
        emit(res, user, req.tenant(), refresh.value(), true);
        return noStore(user);
    }

    /** Troca o refresh token (cookie) por um novo par de cookies. */
    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(HttpServletRequest http, HttpServletResponse res) {
        String presented = AuthCookies.read(http, AuthCookies.REFRESH);
        Optional<RefreshTokenService.Rotation> rotation = Optional.empty();
        String tenant = RefreshTokenService.tenantOf(presented);
        if (tenant != null) {
            try {
                TenantContext.set(tenant);
                rotation = refreshTokens.rotate(presented, http.getHeader("User-Agent"));
            } catch (Exception e) {
                rotation = Optional.empty(); // tenant inexistente/banco indisponivel -> trata como invalido
            }
        }
        if (rotation.isEmpty()) {
            cookies.clear(res);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão expirada.");
        }
        RefreshTokenService.Rotation r = rotation.get();
        emit(res, r.user(), r.tenant(), r.newToken(), r.newToken() != null);
        return noStore(r.user());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse res) {
        String presented = AuthCookies.read(http, AuthCookies.REFRESH);
        String tenant = RefreshTokenService.tenantOf(presented);
        if (tenant != null) {
            try {
                TenantContext.set(tenant);
                refreshTokens.revoke(presented);
            } catch (Exception ignored) {
                // sessao ja inutilizavel; apenas limpa os cookies
            }
        }
        cookies.clear(res);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private void emit(HttpServletResponse res, User user, String tenant, String refreshToken, boolean setRefresh) {
        List<String> roles = user.roleNames().stream().toList();
        List<String> authorities = user.isOwner()
                ? AclCatalog.allPermissions()
                : user.permissionNames().stream().toList();
        String access = jwt.issue(user.getId(), tenant, user.isOwner(), roles, authorities);
        cookies.setAccess(res, access, jwt.ttlSeconds());
        if (setRefresh) cookies.setRefresh(res, refreshToken, refreshTokens.cookieMaxAge());
    }

    private static ResponseEntity<SessionResponse> noStore(User user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SessionResponse(UserDto.from(user)));
    }
}
