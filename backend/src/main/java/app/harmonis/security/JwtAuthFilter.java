package app.harmonis.security;

import app.harmonis.tenancy.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Le o JWT do header Authorization, popula o {@link TenantContext} (claim "tenant") e
 * a autenticacao do Spring Security (authorities = permissoes + ROLE_x). Sempre limpa o
 * contexto de tenant ao fim da requisicao.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    Claims claims = jwt.parse(header.substring(7));
                    String tenant = claims.get("tenant", String.class);
                    TenantContext.set(tenant);

                    List<String> authorities = claims.get("authorities", List.class);
                    List<String> roles = claims.get("roles", List.class);
                    List<SimpleGrantedAuthority> granted = new ArrayList<>();
                    if (authorities != null) authorities.forEach(a -> granted.add(new SimpleGrantedAuthority(a)));
                    if (roles != null) roles.forEach(r -> granted.add(new SimpleGrantedAuthority("ROLE_" + r)));

                    var principal = new AuthPrincipal(
                            Long.parseLong(claims.getSubject()),
                            tenant,
                            Boolean.TRUE.equals(claims.get("owner", Boolean.class)));
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, granted);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception ignored) {
                    // token invalido/expirado -> segue sem autenticacao; endpoints protegidos retornam 401/403
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
