package app.harmonis.security;

import app.harmonis.config.HarmonisProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Emissao e verificacao de JWT (HS256). O token carrega o tenant e as authorities. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(HarmonisProperties props) {
        byte[] secret = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttlMinutes = props.getJwt().getTtlMinutes();
    }

    public String issue(long userId, String tenantSlug, boolean owner,
                        List<String> roles, List<String> authorities) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tenant", tenantSlug)
                .claim("owner", owner)
                .claim("roles", roles)
                .claim("authorities", authorities)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
