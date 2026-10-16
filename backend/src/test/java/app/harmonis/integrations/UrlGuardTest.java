package app.harmonis.integrations;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class UrlGuardTest {

    private final UrlGuard strict = new UrlGuard(false);
    private final UrlGuard lax = new UrlGuard(true);

    @Test
    void aceita_https_publico() {
        assertNull(strict.validateSyntax("https://api.exemplo.com/hook?x=1"));
    }

    @Test
    void rejeita_esquemas_e_formatos_invalidos() {
        assertNotNull(strict.validateSyntax("ftp://exemplo.com/x"));
        assertNotNull(strict.validateSyntax("file:///etc/passwd"));
        assertNotNull(strict.validateSyntax("javascript:alert(1)"));
        assertNotNull(strict.validateSyntax("exemplo.com/sem-esquema"));
        assertNotNull(strict.validateSyntax("http://"));
        assertNotNull(strict.validateSyntax("http://a b.com"));
    }

    @Test
    void rejeita_credenciais_embutidas_na_url() {
        assertNotNull(strict.validateSyntax("https://user:pass@exemplo.com/x"));
    }

    @Test
    void metadados_de_nuvem_e_link_local_sao_sempre_bloqueados() {
        assertNotNull(strict.validateSyntax("http://169.254.169.254/latest/meta-data"));
        assertNotNull(lax.validateSyntax("http://169.254.169.254/latest/meta-data"));
        assertNotNull(lax.validateSyntax("http://0.0.0.0/"));
    }

    @Test
    void rede_privada_depende_da_configuracao() {
        for (String u : new String[]{"http://127.0.0.1:8080/x", "http://10.1.2.3/x", "http://192.168.0.5/x",
                "http://172.16.9.9/x", "http://100.64.1.1/x", "http://[::1]/x"}) {
            assertNotNull(strict.validateSyntax(u), "deveria bloquear " + u);
            assertNull(lax.validateSyntax(u), "deveria permitir " + u + " com allow-private");
        }
    }

    @Test
    void resolve_dns_no_envio_e_bloqueia_loopback_quando_estrito() {
        assertNotNull(strict.validateForSend(URI.create("http://localhost:9/x")));
        assertNull(lax.validateForSend(URI.create("http://localhost:9/x")));
    }

    @Test
    void host_inexistente_gera_mensagem_clara() {
        String msg = lax.validateForSend(URI.create("http://host-que-nao-existe.invalid/x"));
        assertNotNull(msg);
        assertTrue(msg.contains("resolver"));
    }
}
