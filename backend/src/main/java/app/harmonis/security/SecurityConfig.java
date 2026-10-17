package app.harmonis.security;

import app.harmonis.config.HarmonisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final HarmonisProperties props;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, HarmonisProperties props) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.props = props;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF (double-submit): como a sessao agora viaja em cookie, toda requisicao que altera
                // estado precisa enviar o header X-XSRF-TOKEN igual ao cookie XSRF-TOKEN (legivel pelo JS).
                // Login/refresh ficam de fora: nao ha sessao a proteger ou so operam com o refresh
                // cookie SameSite e sua resposta nao e legivel por outra origem.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Sem sessao HTTP: a estrategia padrao regeneraria/limparia o cookie XSRF a cada
                        // requisicao autenticada por JWT (cookie voltava vazio e as mutacoes davam 403).
                        .sessionAuthenticationStrategy(
                                new org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy())
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/refresh"))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Sem autenticacao -> 401 (e nao 403): e o sinal que o front usa para renovar a sessao.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                                org.springframework.http.HttpStatus.UNAUTHORIZED)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // No SS6 o AuthorizationFilter roda tambem no ERROR dispatch; como o
                        // JwtAuthFilter nao re-autentica ali, sem isto qualquer ResponseStatusException
                        // (404/400/409...) sairia como 403 vazio ao ser encaminhada para /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/health", "/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CookieCsrfTokenRepository csrfRepository() {
        HarmonisProperties.Cookies c = props.getCookies();
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookiePath("/");
        repo.setCookieCustomizer(b -> {
            b.secure(c.isSecure()).sameSite(c.getSameSite())
                    .maxAge(Duration.ofDays(props.getJwt().getRefreshTtlDays()));
            if (c.getDomain() != null && !c.getDomain().isBlank()) b.domain(c.getDomain());
        });
        return repo;
    }

    /** Forca a emissao do cookie XSRF-TOKEN em toda resposta (token "deferred" do Spring Security 6). */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(req, res);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(props.getCors().getAllowedOrigins().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
