package app.harmonis.auth;

import app.harmonis.config.HarmonisProperties;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private final Map<String, RefreshToken> byHash = new HashMap<>();
    private final List<RefreshToken> all = new ArrayList<>();
    private RefreshTokenRepository repo;
    private UserRepository users;
    private User user;
    private HarmonisProperties props;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        users = mock(UserRepository.class);
        props = new HarmonisProperties();
        props.getJwt().setRefreshReuseGraceSeconds(10);

        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            byHash.put(t.getTokenHash(), t);
            all.add(t);
            return t;
        });
        when(repo.findByTokenHash(anyString())).thenAnswer(inv -> Optional.ofNullable(byHash.get(inv.<String>getArgument(0))));
        user = mock(User.class);
        when(user.getId()).thenReturn(5L);
        when(user.isActive()).thenReturn(true);
        when(users.findById(5L)).thenReturn(Optional.of(user));
        service = new RefreshTokenService(repo, users, props);
    }

    /** Torna o markUsed do fake funcional: consome o token mais recente ainda nao usado com esse hash. */
    private void fakeMarkUsedConsumes() {
        when(repo.markUsed(any(), any())).thenAnswer(inv -> {
            Instant now = inv.getArgument(1);
            for (RefreshToken t : all) {
                if (t.getUsedAt() == null && t.getRevokedAt() == null && lastLookup == t) {
                    t.setUsedAt(now);
                    return 1;
                }
            }
            return 0;
        });
    }

    private RefreshToken lastLookup;

    private void trackLookups() {
        when(repo.findByTokenHash(anyString())).thenAnswer(inv -> {
            lastLookup = byHash.get(inv.<String>getArgument(0));
            return Optional.ofNullable(lastLookup);
        });
        when(repo.revokeFamily(anyString(), any())).thenAnswer(inv -> {
            all.stream().filter(t -> t.getFamilyId().equals(inv.getArgument(0))).forEach(t -> t.setRevokedAt(Instant.now()));
            return 1;
        });
    }

    @Test
    void tokenEmitidoTemTenantEsoHashEPersistido() {
        RefreshTokenService.Issued i = service.start(5L, "acme", "UA");
        assertTrue(i.value().startsWith("acme."));
        String secret = i.value().substring("acme.".length());
        assertNotNull(byHash.get(RefreshTokenService.hash(secret)));
        assertFalse(byHash.containsKey(secret), "o segredo em claro nao pode ser gravado");
        assertEquals(64, all.get(0).getTokenHash().length());
    }

    @Test
    void rotacaoGeraNovoTokenNaMesmaFamilia() {
        trackLookups();
        fakeMarkUsedConsumes();
        RefreshTokenService.Issued first = service.start(5L, "acme", null);

        RefreshTokenService.Rotation r = service.rotate(first.value(), null).orElseThrow();
        assertNotNull(r.newToken());
        assertNotEquals(first.value(), r.newToken());
        assertEquals(all.get(0).getFamilyId(), all.get(1).getFamilyId());
        assertNotNull(all.get(0).getUsedAt());
    }

    @Test
    void reusoDentroDaToleranciaSoReemiteAccess() {
        trackLookups();
        fakeMarkUsedConsumes();
        RefreshTokenService.Issued first = service.start(5L, "acme", null);
        service.rotate(first.value(), null).orElseThrow();

        RefreshTokenService.Rotation again = service.rotate(first.value(), null).orElseThrow();
        assertNull(again.newToken(), "aba concorrente nao ganha novo refresh");
        assertEquals(2, all.size());
        assertTrue(all.stream().noneMatch(t -> t.getRevokedAt() != null));
    }

    @Test
    void reusoForaDaToleranciaRevogaAFamilia() {
        trackLookups();
        fakeMarkUsedConsumes();
        RefreshTokenService.Issued first = service.start(5L, "acme", null);
        service.rotate(first.value(), null).orElseThrow();
        all.get(0).setUsedAt(Instant.now().minus(Duration.ofMinutes(5)));

        assertTrue(service.rotate(first.value(), null).isEmpty());
        assertTrue(all.stream().allMatch(t -> t.getRevokedAt() != null), "todos os tokens da sessao revogados");
    }

    @Test
    void tokenExpiradoOuDesconhecidoEInvalido() {
        trackLookups();
        fakeMarkUsedConsumes();
        RefreshTokenService.Issued first = service.start(5L, "acme", null);
        all.get(0).setExpiresAt(Instant.now().minusSeconds(1));
        assertTrue(service.rotate(first.value(), null).isEmpty());
        assertTrue(service.rotate("acme.naoexiste", null).isEmpty());
        assertTrue(service.rotate("semponto", null).isEmpty());
        assertTrue(service.rotate(null, null).isEmpty());
    }

    @Test
    void usuarioInativoRevogaASessao() {
        trackLookups();
        fakeMarkUsedConsumes();
        RefreshTokenService.Issued first = service.start(5L, "acme", null);
        when(user.isActive()).thenReturn(false);
        assertTrue(service.rotate(first.value(), null).isEmpty());
        assertNotNull(all.get(0).getRevokedAt());
    }

    @Test
    void validadeAbsolutaLimitaARenovacaoDeslizante() {
        trackLookups();
        fakeMarkUsedConsumes();
        props.getJwt().setRefreshAbsoluteDays(1);
        service = new RefreshTokenService(repo, users, props);
        RefreshTokenService.Issued first = service.start(5L, "acme", null);
        RefreshTokenService.Rotation r = service.rotate(first.value(), null).orElseThrow();
        assertFalse(r.newTokenExpiresAt().isAfter(all.get(0).getFamilyExpiresAt()));
    }

    @Test
    void logoutRevogaAFamilia() {
        trackLookups();
        fakeMarkUsedConsumes();
        RefreshTokenService.Issued first = service.start(5L, "acme", null);
        service.revoke(first.value());
        assertNotNull(all.get(0).getRevokedAt());
    }
}
