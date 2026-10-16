package app.harmonis.integrations;

import app.harmonis.integrations.IntegrationDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntegrationServiceTest {

    private IntegrationRepository repo;
    private FormHookRepository hooks;
    private IntegrationService service;
    private final SecretCipher cipher = new SecretCipher("chave-de-teste-com-tamanho-suficiente");
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repo = mock(IntegrationRepository.class);
        hooks = mock(FormHookRepository.class);
        when(repo.save(any(Integration.class))).thenAnswer(inv -> inv.getArgument(0));
        UrlGuard guard = new UrlGuard(false);
        service = new IntegrationService(repo, hooks, mock(IntegrationLogRepository.class), cipher, guard,
                new WebhookSender(guard, mapper), mapper);
    }

    private static AuthSpec basic(String user, String pass) {
        return new AuthSpec("basic", user, pass, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static AuthSpec typed(String type) {
        return new AuthSpec(type, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static SaveIntegrationRequest req(String name, String url, AuthSpec auth, List<HeaderSpec> headers) {
        return new SaveIntegrationRequest(name, null, url, null, auth, headers, null, null, null, null);
    }

    private static ResponseStatusException fails(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run);
    }

    // ----- criacao e segredos -----

    @Test
    void cria_como_rascunho_cifra_segredos_e_a_view_nao_os_expoe() {
        IntegrationView v = service.create(
                req("ERP", "https://erp.exemplo.com/hook", basic("svc", "S3gredo!"),
                        List.of(new HeaderSpec("X-Token", "valor-secreto", true), new HeaderSpec("X-Tenant", "acme", false))),
                7L);

        assertEquals("draft", v.status());
        assertEquals("POST", v.method());
        assertEquals(2, v.retries());
        assertTrue(v.auth().passwordSet());
        assertEquals("svc", v.auth().username());

        HeaderView secret = v.headers().get(0);
        assertTrue(secret.secret());
        assertNull(secret.value(), "valor de cabecalho secreto nunca sai na API");
        assertTrue(secret.valueSet());
        assertEquals("acme", v.headers().get(1).value());

        Integration saved = savedEntity();
        assertFalse(saved.getAuthJson().contains("S3gredo!"), "senha nao pode ficar em texto puro");
        assertFalse(saved.getHeadersJson().contains("valor-secreto"));
        assertTrue(saved.getAuthJson().contains("enc:v1:"));
        assertEquals(7L, saved.getCreatedBy());
    }

    @Test
    void runtime_decifra_para_o_envio() {
        service.create(req("ERP", "https://erp.exemplo.com/hook", basic("svc", "S3gredo!"),
                List.of(new HeaderSpec("X-Token", "valor-secreto", true))), 1L);
        IntegrationRuntime rt = service.runtime(savedEntity());
        assertEquals("S3gredo!", rt.auth().password());
        assertEquals("valor-secreto", rt.headers().get(0).value());
    }

    @Test
    void segredo_obrigatorio_na_criacao() {
        assertTrue(fails(() -> service.create(req("A", "https://x.com/h", basic("svc", ""), null), 1L))
                .getReason().contains("senha"));
        assertTrue(fails(() -> service.create(req("A", "https://x.com/h", typed("bearer"), null), 1L))
                .getReason().contains("token"));
        assertTrue(fails(() -> service.create(req("A", "https://x.com/h", basic("", "x"), null), 1L))
                .getReason().contains("usuário"));
    }

    @Test
    void atualizacao_com_segredo_vazio_mantem_o_atual_e_com_valor_substitui() {
        service.create(req("ERP", "https://x.com/h", basic("svc", "primeira"),
                List.of(new HeaderSpec("X-Token", "t1", true))), 1L);
        Integration stored = savedEntity();
        stored.setStatus("published");
        when(repo.findById(5L)).thenReturn(Optional.of(stored));
        String cifraOriginal = service.readAuth(stored.getAuthJson()).password();

        service.update(5L, req("ERP", "https://x.com/h", basic("svc2", null),
                List.of(new HeaderSpec("X-Token", null, true))));
        assertEquals(cifraOriginal, service.readAuth(stored.getAuthJson()).password(), "senha mantida");
        assertEquals("svc2", service.readAuth(stored.getAuthJson()).username());
        assertEquals("t1", service.runtime(stored).headers().get(0).value(), "cabecalho secreto mantido");

        service.update(5L, req("ERP", "https://x.com/h", basic("svc2", "segunda"),
                List.of(new HeaderSpec("X-Token", "t2", true))));
        assertEquals("segunda", service.runtime(stored).auth().password());
        assertEquals("t2", service.runtime(stored).headers().get(0).value());
        assertEquals("published", stored.getStatus(), "editar nao despublica");
    }

    @Test
    void trocar_o_tipo_de_autenticacao_nao_herda_segredo_do_tipo_anterior() {
        service.create(req("ERP", "https://x.com/h", basic("svc", "primeira"), null), 1L);
        Integration stored = savedEntity();
        when(repo.findById(5L)).thenReturn(Optional.of(stored));

        ResponseStatusException e = fails(() -> service.update(5L, req("ERP", "https://x.com/h", typed("bearer"), null)));
        assertTrue(e.getReason().contains("token"));
    }

    @Test
    void auth_guarda_so_os_campos_do_tipo_escolhido() {
        AuthSpec sujo = new AuthSpec("none", "u", "p", "k", "v", "header", "t", "Bearer",
                "https://x.com/login", "json", null, null, null, null, null, null);
        service.create(req("A", "https://x.com/h", sujo, null), 1L);
        AuthSpec stored = service.readAuth(savedEntity().getAuthJson());
        assertEquals("none", stored.type());
        assertNull(stored.password());
        assertNull(stored.token());
        assertNull(stored.tokenUrl());
    }

    @Test
    void apikey_valida_local_e_nome() {
        AuthSpec ok = new AuthSpec("apikey", null, null, "X-Api-Key", "abc", "header", null, null,
                null, null, null, null, null, null, null, null);
        assertDoesNotThrow(() -> service.create(req("A", "https://x.com/h", ok, null), 1L));

        AuthSpec badLoc = new AuthSpec("apikey", null, null, "k", "abc", "cookie", null, null,
                null, null, null, null, null, null, null, null);
        fails(() -> service.create(req("B", "https://x.com/h", badLoc, null), 1L));

        AuthSpec badName = new AuthSpec("apikey", null, null, "nome com espaco", "abc", "query", null, null,
                null, null, null, null, null, null, null, null);
        fails(() -> service.create(req("C", "https://x.com/h", badName, null), 1L));
    }

    @Test
    void preauth_aplica_padroes_e_valida_a_url_de_login() {
        AuthSpec p = new AuthSpec("bearer_preauth", "svc", "pw", null, null, null, null, null,
                "https://x.com/login", null, null, null, null, null, null, null);
        service.create(req("P", "https://x.com/h", p, null), 1L);
        AuthSpec s = service.readAuth(savedEntity().getAuthJson());
        assertEquals("json", s.tokenBodyFormat());
        assertEquals("username", s.usernameField());
        assertEquals("password", s.passwordField());
        assertEquals("access_token", s.tokenPath());
        assertEquals("expires_in", s.expiresInPath());
        assertEquals(3000, s.tokenTtlSeconds());
        assertEquals("Bearer", s.tokenPrefix());

        AuthSpec semUrl = new AuthSpec("bearer_preauth", "svc", "pw", null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        fails(() -> service.create(req("Q", "https://x.com/h", semUrl, null), 1L));
        AuthSpec metadata = new AuthSpec("bearer_preauth", "svc", "pw", null, null, null, null, null,
                "http://169.254.169.254/token", null, null, null, null, null, null, null);
        assertTrue(fails(() -> service.create(req("R", "https://x.com/h", metadata, null), 1L))
                .getReason().contains("Pré-autenticação"));
    }

    // ----- cabecalhos -----

    @Test
    void cabecalhos_reservados_invalidos_e_repetidos_sao_rejeitados() {
        for (String reservado : new String[]{"Host", "Content-Length", "content-type", "Transfer-Encoding",
                "Connection", "X-Harmonis-Signature", "X-Harmonis-Event"}) {
            fails(() -> service.create(req("A", "https://x.com/h", AuthSpec.none(),
                    List.of(new HeaderSpec(reservado, "v", false))), 1L));
        }
        fails(() -> service.create(req("A", "https://x.com/h", AuthSpec.none(),
                List.of(new HeaderSpec("nome invalido", "v", false))), 1L));
        fails(() -> service.create(req("A", "https://x.com/h", AuthSpec.none(),
                List.of(new HeaderSpec("X-A", "linha1\r\nInjetado: 1", false))), 1L));
        fails(() -> service.create(req("A", "https://x.com/h", AuthSpec.none(),
                List.of(new HeaderSpec("X-A", "1", false), new HeaderSpec("x-a", "2", false))), 1L));
        fails(() -> service.create(req("A", "https://x.com/h", AuthSpec.none(),
                List.of(new HeaderSpec("X-A", " ", false))), 1L));
    }

    @Test
    void authorization_customizado_conflita_com_auth_gerenciada_mas_vale_em_none_e_headers() {
        List<HeaderSpec> custom = List.of(new HeaderSpec("Authorization", "Custom abc", false));
        fails(() -> service.create(req("A", "https://x.com/h", basic("u", "p"), custom), 1L));
        assertDoesNotThrow(() -> service.create(req("B", "https://x.com/h", AuthSpec.none(), custom), 1L));
        assertDoesNotThrow(() -> service.create(req("C", "https://x.com/h", typed("headers"), custom), 1L));
    }

    @Test
    void tipo_headers_exige_ao_menos_um_cabecalho() {
        assertTrue(fails(() -> service.create(req("A", "https://x.com/h", typed("headers"), List.of()), 1L))
                .getReason().contains("cabeçalho"));
    }

    // ----- demais validacoes -----

    @Test
    void url_invalida_credenciais_embutidas_e_metadados_sao_rejeitadas() {
        fails(() -> service.create(req("A", "ftp://x.com/h", AuthSpec.none(), null), 1L));
        fails(() -> service.create(req("A", "https://user:pw@x.com/h", AuthSpec.none(), null), 1L));
        fails(() -> service.create(req("A", "http://169.254.169.254/latest", AuthSpec.none(), null), 1L));
        fails(() -> service.create(req("A", "http://127.0.0.1:8080/h", AuthSpec.none(), null), 1L));
    }

    @Test
    void metodo_timeout_e_retentativas_tem_limites() {
        fails(() -> service.create(new SaveIntegrationRequest("A", null, "https://x.com/h", "DELETE",
                AuthSpec.none(), null, null, null, null, null), 1L));
        fails(() -> service.create(new SaveIntegrationRequest("A", null, "https://x.com/h", "PUT",
                AuthSpec.none(), null, null, null, 0, null), 1L));
        fails(() -> service.create(new SaveIntegrationRequest("A", null, "https://x.com/h", "PUT",
                AuthSpec.none(), null, null, null, 61, null), 1L));
        fails(() -> service.create(new SaveIntegrationRequest("A", null, "https://x.com/h", "PUT",
                AuthSpec.none(), null, null, null, null, 6), 1L));
        assertDoesNotThrow(() -> service.create(new SaveIntegrationRequest("A", null, "https://x.com/h", "patch",
                AuthSpec.none(), null, null, null, 60, 0), 1L));
    }

    @Test
    void nome_duplicado_e_conflito() {
        when(repo.existsByNameIgnoreCase("ERP")).thenReturn(true);
        ResponseStatusException e = fails(() -> service.create(req("ERP", "https://x.com/h", AuthSpec.none(), null), 1L));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void assinatura_hmac_pode_ser_definida_mantida_e_removida() {
        service.create(new SaveIntegrationRequest("A", null, "https://x.com/h", null, AuthSpec.none(), null,
                "meu-segredo", null, null, null), 1L);
        Integration stored = savedEntity();
        assertTrue(cipher.isEncrypted(stored.getSigningSecret()));
        assertEquals("meu-segredo", service.runtime(stored).signingSecret());
        when(repo.findById(5L)).thenReturn(Optional.of(stored));

        service.update(5L, new SaveIntegrationRequest("A", null, "https://x.com/h", null, AuthSpec.none(), null,
                null, null, null, null));
        assertEquals("meu-segredo", service.runtime(stored).signingSecret(), "nulo mantem");

        IntegrationView v = service.update(5L, new SaveIntegrationRequest("A", null, "https://x.com/h", null,
                AuthSpec.none(), null, null, true, null, null));
        assertFalse(v.signed());
        assertNull(stored.getSigningSecret());
    }

    // ----- publicacao e exclusao -----

    @Test
    void publicar_e_despublicar() {
        service.create(req("A", "https://x.com/h", AuthSpec.none(), null), 1L);
        Integration stored = savedEntity();
        when(repo.findById(5L)).thenReturn(Optional.of(stored));

        IntegrationView pub = service.publish(5L);
        assertEquals("published", pub.status());
        assertNotNull(pub.publishedAt());
        assertEquals("draft", service.unpublish(5L).status());
    }

    @Test
    void exclusao_bloqueada_enquanto_houver_vinculo() {
        service.create(req("A", "https://x.com/h", AuthSpec.none(), null), 1L);
        Integration stored = savedEntity();
        when(repo.findById(5L)).thenReturn(Optional.of(stored));

        when(hooks.countByIntegrationId(5L)).thenReturn(2L);
        ResponseStatusException e = fails(() -> service.delete(5L));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(repo, never()).delete(any());

        when(hooks.countByIntegrationId(5L)).thenReturn(0L);
        service.delete(5L);
        verify(repo).delete(stored);
    }

    @Test
    void inexistente_e_404() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, fails(() -> service.get(99L)).getStatusCode());
    }

    private Integration savedEntity() {
        var captor = org.mockito.ArgumentCaptor.forClass(Integration.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
