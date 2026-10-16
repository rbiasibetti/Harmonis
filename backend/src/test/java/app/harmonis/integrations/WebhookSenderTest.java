package app.harmonis.integrations;

import app.harmonis.integrations.IntegrationDtos.AuthSpec;
import app.harmonis.integrations.IntegrationDtos.HeaderSpec;
import app.harmonis.integrations.IntegrationDtos.NameValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Exercita o cliente contra um servidor HTTP local real (loopback liberado so no teste). */
class WebhookSenderTest {

    private record Seen(String method, String path, String query, Map<String, String> headers, String body) {}

    private HttpServer server;
    private String base;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private final Map<String, Function<Seen, int[]>> routes = new ConcurrentHashMap<>();
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final WebhookSender sender = new WebhookSender(new UrlGuard(true), new ObjectMapper());

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> h = new ConcurrentHashMap<>();
        ex.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.get(0)));
        Seen s = new Seen(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestURI().getRawQuery(), h, body);
        seen.add(s);
        Function<Seen, int[]> route = routes.get(s.path());
        int status = route == null ? 200 : route.apply(s)[0];
        String resp = responses.getOrDefault(s.path(), "{\"ok\":true}");
        if (status >= 300 && status < 400) ex.getResponseHeaders().add("Location", base + "/destino");
        byte[] out = resp.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
        if (out.length > 0) ex.getResponseBody().write(out);
        ex.close();
    }

    private IntegrationRuntime rt(String path, AuthSpec auth, List<HeaderSpec> headers, String signing) {
        return new IntegrationRuntime(1L, base + path, "POST", auth, headers, signing, 5);
    }

    private static AuthSpec auth(String type) {
        return new AuthSpec(type, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private Seen last() { return seen.get(seen.size() - 1); }

    @Test
    void envia_json_com_cabecalhos_padrao_e_sem_autenticacao() {
        var r = sender.send("acme", rt("/h", AuthSpec.none(), List.of(), null), "{\"a\":1}", "approved", "d-1");
        assertTrue(r.ok());
        assertEquals(200, r.httpStatus());
        Seen s = last();
        assertEquals("POST", s.method());
        assertEquals("{\"a\":1}", s.body());
        assertTrue(s.headers().get("content-type").startsWith("application/json"));
        assertEquals("approved", s.headers().get("x-harmonis-event"));
        assertEquals("d-1", s.headers().get("x-harmonis-delivery"));
        assertNull(s.headers().get("authorization"));
        assertNull(s.headers().get("x-harmonis-signature"));
    }

    @Test
    void basic_envia_usuario_e_senha_em_base64() {
        AuthSpec a = new AuthSpec("basic", "maria", "s3nha", null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertTrue(sender.send("acme", rt("/h", a, List.of(), null), "{}", "submitted", "d").ok());
        String expected = "Basic " + Base64.getEncoder().encodeToString("maria:s3nha".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, last().headers().get("authorization"));
    }

    @Test
    void apikey_no_cabecalho() {
        AuthSpec a = new AuthSpec("apikey", null, null, "X-Api-Key", "abc123", "header", null, null,
                null, null, null, null, null, null, null, null);
        assertTrue(sender.send("acme", rt("/h", a, List.of(), null), "{}", "submitted", "d").ok());
        assertEquals("abc123", last().headers().get("x-api-key"));
    }

    @Test
    void apikey_na_query_e_url_do_log_mascara_o_valor() {
        AuthSpec a = new AuthSpec("apikey", null, null, "api_key", "abc 123", "query", null, null,
                null, null, null, null, null, null, null, null);
        var r = sender.send("acme", rt("/h?fixo=1", a, List.of(), null), "{}", "submitted", "d");
        assertTrue(r.ok());
        assertEquals("fixo=1&api_key=abc+123", last().query());
        assertTrue(r.logUrl().contains("api_key=***"));
        assertFalse(r.logUrl().contains("abc"));
        assertTrue(r.logUrl().contains("fixo=1"));
    }

    @Test
    void bearer_estatico_com_prefixo_padrao_e_customizado() {
        AuthSpec a = new AuthSpec("bearer", null, null, null, null, null, "tok-1", "Bearer",
                null, null, null, null, null, null, null, null);
        assertTrue(sender.send("acme", rt("/h", a, List.of(), null), "{}", "approved", "d").ok());
        assertEquals("Bearer tok-1", last().headers().get("authorization"));

        AuthSpec b = new AuthSpec("bearer", null, null, null, null, null, "tok-2", "Token",
                null, null, null, null, null, null, null, null);
        assertTrue(sender.send("acme", rt("/h", b, List.of(), null), "{}", "approved", "d").ok());
        assertEquals("Token tok-2", last().headers().get("authorization"));
    }

    @Test
    void cabecalhos_customizados_sao_enviados_e_sobrepoem_padroes() {
        var headers = List.of(new HeaderSpec("X-Tenant", "acme", false), new HeaderSpec("Accept", "text/plain", false));
        assertTrue(sender.send("acme", rt("/h", auth("headers"), headers, null), "{}", "approved", "d").ok());
        assertEquals("acme", last().headers().get("x-tenant"));
        assertEquals("text/plain", last().headers().get("accept"));
    }

    @Test
    void assinatura_hmac_sha256_confere_com_o_corpo() {
        String body = "{\"evento\":\"ok\"}";
        assertTrue(sender.send("acme", rt("/h", AuthSpec.none(), List.of(), "segredo"), body, "approved", "d").ok());
        String expected = "sha256=" + WebhookSender.hmacHex("segredo", body.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, last().headers().get("x-harmonis-signature"));
        assertEquals(71, expected.length()); // "sha256=" + 64 hex
    }

    // ----- pre-autenticacao -----

    private AuthSpec preauth(String format) {
        return new AuthSpec("bearer_preauth", "svc", "pw", null, null, null, null, "Bearer",
                base + "/login", format, "user", "pass", List.of(new NameValue("grant_type", "password")),
                "data.token", "data.expires", 3000);
    }

    @Test
    void preauth_json_obtem_token_e_usa_como_bearer() {
        responses.put("/login", "{\"data\":{\"token\":\"T-123\",\"expires\":3600}}");
        var r = sender.send("acme", rt("/h", preauth("json"), List.of(), null), "{}", "approved", "d");
        assertTrue(r.ok(), r.error());

        Seen login = seen.stream().filter(s -> s.path().equals("/login")).findFirst().orElseThrow();
        assertTrue(login.headers().get("content-type").startsWith("application/json"));
        assertEquals("{\"user\":\"svc\",\"pass\":\"pw\",\"grant_type\":\"password\"}", login.body());
        assertEquals("Bearer T-123", last().headers().get("authorization"));
        assertEquals("/h", last().path());
    }

    @Test
    void preauth_form_urlencoded() {
        responses.put("/login", "{\"data\":{\"token\":\"T-9\"}}");
        assertTrue(sender.send("acme", rt("/h", preauth("form"), List.of(), null), "{}", "approved", "d").ok());
        Seen login = seen.stream().filter(s -> s.path().equals("/login")).findFirst().orElseThrow();
        assertTrue(login.headers().get("content-type").startsWith("application/x-www-form-urlencoded"));
        assertEquals("user=svc&pass=pw&grant_type=password", login.body());
    }

    @Test
    void preauth_reaproveita_token_em_cache() {
        responses.put("/login", "{\"data\":{\"token\":\"T-1\",\"expires\":3600}}");
        IntegrationRuntime rt = rt("/h", preauth("json"), List.of(), null);
        assertTrue(sender.send("acme", rt, "{}", "approved", "d1").ok());
        assertTrue(sender.send("acme", rt, "{}", "approved", "d2").ok());
        long logins = seen.stream().filter(s -> s.path().equals("/login")).count();
        assertEquals(1, logins, "o segundo envio deve reutilizar o token");
    }

    @Test
    void preauth_renova_o_token_quando_o_destino_responde_401() {
        AtomicInteger logins = new AtomicInteger();
        AtomicInteger hooks = new AtomicInteger();
        routes.put("/login", s -> new int[]{200});
        routes.put("/h", s -> new int[]{hooks.incrementAndGet() == 1 ? 401 : 200});
        // token diferente a cada login
        server.removeContext("/");
        server.createContext("/", ex -> {
            if (ex.getRequestURI().getPath().equals("/login")) {
                ex.getRequestBody().readAllBytes();
                byte[] out = ("{\"data\":{\"token\":\"T-" + logins.incrementAndGet() + "\",\"expires\":3600}}")
                        .getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            } else {
                handle(ex);
            }
        });
        var r = sender.send("acme", rt("/h", preauth("json"), List.of(), null), "{}", "approved", "d");
        assertTrue(r.ok(), r.error());
        assertEquals(2, logins.get());
        assertEquals("Bearer T-2", last().headers().get("authorization"));
    }

    @Test
    void preauth_com_falha_no_login_nao_chama_o_destino() {
        routes.put("/login", s -> new int[]{401});
        var r = sender.send("acme", rt("/h", preauth("json"), List.of(), null), "{}", "approved", "d");
        assertFalse(r.ok());
        assertTrue(r.error().contains("HTTP 401"));
        assertFalse(r.retryable());
        assertTrue(seen.stream().noneMatch(s -> s.path().equals("/h")));
    }

    @Test
    void preauth_resposta_sem_o_campo_do_token() {
        responses.put("/login", "{\"outro\":1}");
        var r = sender.send("acme", rt("/h", preauth("json"), List.of(), null), "{}", "approved", "d");
        assertFalse(r.ok());
        assertTrue(r.error().contains("data.token"));
    }

    // ----- classificacao de resultados -----

    @Test
    void status_5xx_e_429_sao_retentaveis_4xx_nao() {
        routes.put("/e500", s -> new int[]{500});
        routes.put("/e429", s -> new int[]{429});
        routes.put("/e400", s -> new int[]{400});
        var a = sender.send("acme", rt("/e500", AuthSpec.none(), List.of(), null), "{}", "approved", "d");
        var b = sender.send("acme", rt("/e429", AuthSpec.none(), List.of(), null), "{}", "approved", "d");
        var c = sender.send("acme", rt("/e400", AuthSpec.none(), List.of(), null), "{}", "approved", "d");
        assertTrue(a.retryable());
        assertTrue(b.retryable());
        assertFalse(c.retryable());
        assertFalse(a.ok());
        assertEquals(400, c.httpStatus());
        assertTrue(c.error().contains("400"));
        assertNotNull(c.responseBody());
    }

    @Test
    void redirecionamento_nao_e_seguido() {
        routes.put("/r", s -> new int[]{302});
        var r = sender.send("acme", rt("/r", AuthSpec.none(), List.of(), null), "{}", "approved", "d");
        assertFalse(r.ok());
        assertEquals(302, r.httpStatus());
        assertTrue(seen.stream().noneMatch(s -> s.path().equals("/destino")));
    }

    @Test
    void falha_de_conexao_e_retentavel() throws IOException {
        int port;
        try (var s = new java.net.ServerSocket(0)) { port = s.getLocalPort(); }
        IntegrationRuntime rt = new IntegrationRuntime(1L, "http://127.0.0.1:" + port + "/x", "POST",
                AuthSpec.none(), List.of(), null, 3);
        var r = sender.send("acme", rt, "{}", "approved", "d");
        assertFalse(r.ok());
        assertTrue(r.retryable());
        assertNull(r.httpStatus());
        assertTrue(r.error().startsWith("Falha de rede"));
    }

    @Test
    void timeout_e_retentavel_e_descrito() {
        server.removeContext("/");
        server.createContext("/", ex -> {
            try { Thread.sleep(2500); } catch (InterruptedException ignored) { }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        IntegrationRuntime rt = new IntegrationRuntime(1L, base + "/lento", "POST",
                AuthSpec.none(), List.of(), null, 1);
        var r = sender.send("acme", rt, "{}", "approved", "d");
        assertFalse(r.ok());
        assertTrue(r.retryable());
        assertTrue(r.error().contains("Tempo esgotado"), r.error());
    }

    @Test
    void ssrf_bloqueia_destino_privado_quando_estrito_sem_abrir_conexao() {
        WebhookSender strict = new WebhookSender(new UrlGuard(false), new ObjectMapper());
        var r = strict.send("acme", rt("/h", AuthSpec.none(), List.of(), null), "{}", "approved", "d");
        assertFalse(r.ok());
        assertFalse(r.retryable());
        assertTrue(seen.isEmpty(), "nenhuma requisicao deve ter chegado ao destino");
    }

    @Test
    void resposta_grande_e_truncada_no_log() {
        responses.put("/h", "x".repeat(20000));
        var r = sender.send("acme", rt("/h", AuthSpec.none(), List.of(), null), "{}", "approved", "d");
        assertTrue(r.ok());
        assertTrue(r.responseBody().length() <= WebhookSender.MAX_STORED_RESPONSE_CHARS + 1);
    }

    @Test
    void url_do_log_mascara_parametros_sensiveis_da_propria_url() {
        assertEquals("https://x.com/p?a=1&token=***&Key=***",
                WebhookSender.maskedUrl("https://x.com/p?a=1&token=SEGREDO&Key=OUTRO", null));
        assertEquals("https://x.com/p", WebhookSender.maskedUrl("https://x.com/p", null));
    }
}
