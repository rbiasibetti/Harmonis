package app.harmonis.auth;

import app.harmonis.identity.AclCatalog;
import app.harmonis.identity.User;
import app.harmonis.identity.UserDto;
import app.harmonis.identity.UserRepository;
import app.harmonis.security.JwtService;
import app.harmonis.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    public record LoginRequest(
            @NotBlank String tenant,
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record LoginResponse(String token, UserDto user) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        // Define o tenant ANTES de qualquer acesso a dados: o datasource roteia por aqui.
        TenantContext.set(req.tenant());

        User user = users.findByEmailIgnoreCase(req.email())
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas."));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas.");
        }

        List<String> roles = user.roleNames().stream().toList();
        List<String> authorities = user.isOwner()
                ? AclCatalog.allPermissions()
                : user.permissionNames().stream().toList();

        String token = jwt.issue(user.getId(), req.tenant(), user.isOwner(), roles, authorities);
        return new LoginResponse(token, UserDto.from(user));
    }
}
