package app.harmonis.security;

import app.harmonis.config.HarmonisProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService service() {
        HarmonisProperties props = new HarmonisProperties();
        props.getJwt().setSecret("chave-de-teste-com-pelo-menos-32-bytes-aqui!!");
        props.getJwt().setTtlMinutes(60);
        return new JwtService(props);
    }

    @Test
    void emite_e_le_claims() {
        JwtService jwt = service();
        String token = jwt.issue(7L, "acme", true, List.of("admin"),
                List.of("forms.view", "users.view"));

        Claims claims = jwt.parse(token);
        assertEquals("7", claims.getSubject());
        assertEquals("acme", claims.get("tenant", String.class));
        assertEquals(Boolean.TRUE, claims.get("owner", Boolean.class));
        assertTrue(((List<?>) claims.get("authorities")).contains("forms.view"));
        assertTrue(((List<?>) claims.get("roles")).contains("admin"));
    }

    @Test
    void token_adulterado_e_rejeitado() {
        JwtService jwt = service();
        String token = jwt.issue(1L, "acme", false, List.of("member"), List.of("forms.view"));
        // Troca um caractere no MEIO da assinatura (os ultimos bits do base64 podem nao alterar os bytes).
        int i = token.lastIndexOf('.') + 5;
        char flipped = token.charAt(i) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, i) + flipped + token.substring(i + 1);
        assertThrows(Exception.class, () -> jwt.parse(tampered));
    }

    @Test
    void token_de_outra_chave_e_rejeitado() {
        JwtService a = service();
        HarmonisProperties other = new HarmonisProperties();
        other.getJwt().setSecret("outra-chave-totalmente-diferente-com-32+bytes!!");
        JwtService b = new JwtService(other);

        String token = a.issue(1L, "acme", false, List.of("member"), List.of());
        assertThrows(Exception.class, () -> b.parse(token));
    }

    @Test
    void emiteIssuerAudienceEJti() {
        JwtService jwt = service();
        Claims c = jwt.parse(jwt.issue(1L, "acme", false, List.of(), List.of()));
        assertEquals("harmonis", c.getIssuer());
        assertTrue(c.getAudience().contains("harmonis-web"));
        assertNotNull(c.getId());
    }

    @Test
    void tokenDeOutraAudienciaERejeitado() {
        JwtService a = service();
        HarmonisProperties other = new HarmonisProperties();
        other.getJwt().setSecret("chave-de-teste-com-pelo-menos-32-bytes-aqui!!");
        other.getJwt().setAudience("outro-sistema");
        String token = new JwtService(other).issue(1L, "acme", false, List.of(), List.of());
        assertThrows(Exception.class, () -> a.parse(token));
    }

    @Test
    void segredoCurtoNaoSobe() {
        HarmonisProperties p = new HarmonisProperties();
        p.getJwt().setSecret("curta");
        assertThrows(IllegalStateException.class, () -> new JwtService(p));
    }
}
