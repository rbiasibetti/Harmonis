package app.harmonis.auth;

import app.harmonis.config.HarmonisProperties;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Ciclo de vida dos refresh tokens (boas praticas OAuth 2.0 / RFC 9700):
 * opaco e aleatorio, so o hash e guardado, rotacao a cada uso, deteccao de reuso
 * (revoga a familia inteira) com curta janela de tolerancia para requisicoes concorrentes
 * (abas), validade deslizante limitada por uma validade absoluta da sessao.
 *
 * <p>O valor entregue ao navegador tem o formato {@code <tenant>.<segredo>} — o tenant e
 * necessario para localizar o banco correto antes de qualquer consulta.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Resultado de uma renovacao. {@code newToken} e nulo quando so o access token deve ser reemitido. */
    public record Rotation(User user, String tenant, String newToken, Instant newTokenExpiresAt) {}

    /** Token recem-emitido a entregar no cookie. */
    public record Issued(String value, Instant expiresAt) {}

    private final RefreshTokenRepository repo;
    private final UserRepository users;
    private final Duration ttl;
    private final Duration absolute;
    private final Duration grace;

    public RefreshTokenService(RefreshTokenRepository repo, UserRepository users, HarmonisProperties props) {
        this.repo = repo;
        this.users = users;
        this.ttl = Duration.ofDays(props.getJwt().getRefreshTtlDays());
        this.absolute = Duration.ofDays(props.getJwt().getRefreshAbsoluteDays());
        this.grace = Duration.ofSeconds(props.getJwt().getRefreshReuseGraceSeconds());
    }

    public Duration cookieMaxAge() { return ttl; }

    /** Inicia uma sessao (nova familia). Requer TenantContext definido. */
    @Transactional
    public Issued start(long userId, String tenant, String userAgent) {
        Instant now = Instant.now();
        return create(userId, tenant, UUID.randomUUID().toString(), now.plus(absolute), now, userAgent);
    }

    /** Valida e rotaciona. Requer TenantContext do tenant do token. Retorna vazio se invalido. */
    @Transactional
    public Optional<Rotation> rotate(String presented, String userAgent) {
        String secret = secretOf(presented);
        String tenant = tenantOf(presented);
        if (secret == null || tenant == null) return Optional.empty();

        Instant now = Instant.now();
        RefreshToken t = repo.findByTokenHash(hash(secret)).orElse(null);
        if (t == null || t.getRevokedAt() != null) return Optional.empty();
        if (!t.getExpiresAt().isAfter(now) || !t.getFamilyExpiresAt().isAfter(now)) return Optional.empty();

        User user = users.findById(t.getUserId()).filter(User::isActive).orElse(null);
        if (user == null) {
            repo.revokeFamily(t.getFamilyId(), now);
            return Optional.empty();
        }

        boolean consumed = t.getUsedAt() == null && repo.markUsed(t.getId(), now) == 1;
        if (consumed) {
            Issued next = create(user.getId(), tenant, t.getFamilyId(), t.getFamilyExpiresAt(), now, userAgent);
            return Optional.of(new Rotation(user, tenant, next.value(), next.expiresAt()));
        }

        // Ja usado: dentro da tolerancia e requisicao concorrente legitima (outra aba) -> so novo access token.
        Instant usedAt = t.getUsedAt() != null ? t.getUsedAt() : now;
        if (Duration.between(usedAt, now).compareTo(grace) <= 0) {
            return Optional.of(new Rotation(user, tenant, null, null));
        }
        // Fora da tolerancia: token roubado/reapresentado -> encerra a sessao inteira.
        log.warn("Reuso de refresh token detectado (usuario {}, tenant {}); sessao revogada.", user.getId(), tenant);
        repo.revokeFamily(t.getFamilyId(), now);
        return Optional.empty();
    }

    /** Encerra a sessao do token apresentado (logout). Silencioso se desconhecido. */
    @Transactional
    public void revoke(String presented) {
        String secret = secretOf(presented);
        if (secret == null) return;
        repo.findByTokenHash(hash(secret)).ifPresent(t -> repo.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    /** Encerra todas as sessoes do usuario (ex.: troca de senha, desativacao). */
    @Transactional
    public void revokeAll(long userId) {
        repo.revokeAllOfUser(userId, Instant.now());
    }

    /** Remove tokens vencidos (a ser chamado periodicamente). */
    @Transactional
    public int purgeExpired() {
        Instant now = Instant.now();
        return repo.deleteExpired(now, now.minus(Duration.ofDays(1)));
    }

    private Issued create(long userId, String tenant, String family, Instant familyExpires,
                          Instant now, String userAgent) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setFamilyId(family);
        t.setTokenHash(hash(secret));
        Instant expires = now.plus(ttl);
        if (expires.isAfter(familyExpires)) expires = familyExpires;
        t.setExpiresAt(expires);
        t.setFamilyExpiresAt(familyExpires);
        t.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())));
        repo.save(t);
        return new Issued(tenant + "." + secret, expires);
    }

    static String tenantOf(String presented) {
        if (presented == null) return null;
        int i = presented.lastIndexOf('.');
        return i > 0 ? presented.substring(0, i) : null;
    }

    static String secretOf(String presented) {
        if (presented == null) return null;
        int i = presented.lastIndexOf('.');
        return i > 0 && i < presented.length() - 1 ? presented.substring(i + 1) : null;
    }

    static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
