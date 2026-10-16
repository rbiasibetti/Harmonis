package app.harmonis.integrations;

import app.harmonis.integrations.IntegrationDtos.AuthSpec;
import app.harmonis.integrations.IntegrationDtos.HeaderSpec;
import app.harmonis.integrations.IntegrationDtos.NameValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente HTTP dos webhooks: monta a requisicao (autenticacao, cabecalhos, assinatura HMAC),
 * protege contra SSRF, nao segue redirecionamentos e classifica o resultado.
 * Mantem cache em memoria dos tokens obtidos na pre-autenticacao.
 */
@Component
public class WebhookSender {

    static final int MAX_STORED_RESPONSE_CHARS = 4000;
    private static final int MAX_READ_BYTES = 32 * 1024;
    private static final long TOKEN_SKEW_SECONDS = 30;
    private static final Set<String> SENSITIVE_QUERY = Set.of(
            "key", "apikey", "api_key", "api-key", "token", "access_token", "secret",
            "password", "pwd", "pass", "sig", "signature", "auth", "authorization", "code");

    /**
     * Resultado de um envio. {@code logUrl} ja esta mascarado. {@code retryable}: vale a pena tentar
     * de novo (falha de rede/timeout, 408, 429, 5xx).
     */
    public record Result(boolean ok, Integer httpStatus, String responseBody, String error,
                         int durationMs, String logUrl, boolean retryable) {}

    private record CachedToken(String value, long expiresAtMs) {}

    private static class TokenException extends Exception {
        final boolean retryable;
        TokenException(String message, boolean retryable) { super(message); this.retryable = retryable; }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final UrlGuard guard;
    private final ObjectMapper mapper;
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public WebhookSender(UrlGuard guard, ObjectMapper mapper) {
        this.guard = guard;
        this.mapper = mapper;
    }

    /** Descarta tokens em cache da integracao (apos editar credenciais, por exemplo). */
    public void invalidateTokens(Long integrationId) {
        String marker = ":" + integrationId + ":";
        tokens.keySet().removeIf(k -> k.contains(marker));
    }

    public Result send(String tenant, IntegrationRuntime rt, String body, String event, String deliveryId) {
        long t0 = System.nanoTime();
        String logUrl = maskedUrl(rt.url(), null);
        try {
            AuthSpec auth = rt.auth() == null ? AuthSpec.none() : rt.auth();
            boolean apiKeyInQuery = "apikey".equals(auth.type()) && "query".equals(auth.keyIn());
            URI uri = URI.create(rt.url().trim());
            if (apiKeyInQuery) uri = withQuery(uri, auth.keyName(), auth.keyValue());
            logUrl = maskedUrl(rt.url(), apiKeyInQuery ? auth.keyName() : null);

            String blocked = guard.validateForSend(uri);
            if (blocked != null) {
                return result(false, null, null, blocked, t0, logUrl, false);
            }

            boolean preauth = "bearer_preauth".equals(auth.type());
            for (int pass = 0; pass < 2; pass++) {
                String bearer = preauth ? token(tenant, rt, pass == 1) : null;
                HttpRequest req = buildRequest(rt, uri, body, event, deliveryId, bearer);
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                String text = readLimited(resp.body());
                int code = resp.statusCode();

                if (code == 401 && preauth && pass == 0) {
                    continue; // token possivelmente expirado/revogado: renova e tenta uma vez
                }
                if (code >= 200 && code < 300) {
                    return result(true, code, text, null, t0, logUrl, false);
                }
                String error = code >= 300 && code < 400
                        ? "Redirecionamento não seguido (HTTP " + code + ")."
                        : "O destino respondeu HTTP " + code + ".";
                boolean retry = code == 408 || code == 429 || code >= 500;
                return result(false, code, text, error, t0, logUrl, retry);
            }
            return result(false, 401, null, "Não autorizado (HTTP 401) mesmo após renovar o token.", t0, logUrl, false);
        } catch (TokenException e) {
            return result(false, null, null, e.getMessage(), t0, logUrl, e.retryable);
        } catch (HttpTimeoutException e) {
            return result(false, null, null,
                    "Tempo esgotado após " + rt.timeoutSeconds() + "s.", t0, logUrl, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(false, null, null, "Envio interrompido.", t0, logUrl, false);
        } catch (IOException e) {
            return result(false, null, null, "Falha de rede: " + brief(e), t0, logUrl, true);
        } catch (IllegalArgumentException e) {
            return result(false, null, null, "Configuração inválida: " + brief(e), t0, logUrl, false);
        } catch (RuntimeException e) {
            return result(false, null, null, "Erro inesperado: " + brief(e), t0, logUrl, false);
        }
    }

    // ----- requisicao -----

    private HttpRequest buildRequest(IntegrationRuntime rt, URI uri, String body, String event,
                                     String deliveryId, String bearer) {
        AuthSpec auth = rt.auth() == null ? AuthSpec.none() : rt.auth();
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);

        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(1, rt.timeoutSeconds())))
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("Accept", "application/json, */*")
                .setHeader("User-Agent", "Harmonis-Webhook/1.0")
                .setHeader("X-Harmonis-Event", event)
                .setHeader("X-Harmonis-Delivery", deliveryId);

        if (rt.signingSecret() != null && !rt.signingSecret().isEmpty()) {
            b.setHeader("X-Harmonis-Signature", "sha256=" + hmacHex(rt.signingSecret(), payload));
        }
        if (rt.headers() != null) {
            for (HeaderSpec h : rt.headers()) {
                if (h.name() != null && !h.name().isBlank() && h.value() != null) {
                    b.setHeader(h.name().trim(), h.value());
                }
            }
        }

        switch (auth.type() == null ? "none" : auth.type()) {
            case "basic" -> {
                String raw = Objects.toString(auth.username(), "") + ":" + Objects.toString(auth.password(), "");
                b.setHeader("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            case "apikey" -> {
                if (!"query".equals(auth.keyIn())) b.setHeader(auth.keyName(), auth.keyValue());
            }
            case "bearer" -> b.setHeader("Authorization", prefix(auth) + " " + auth.token());
            case "bearer_preauth" -> b.setHeader("Authorization", prefix(auth) + " " + bearer);
            default -> { /* none / headers: so os cabecalhos customizados */ }
        }
        return b.method(rt.method(), HttpRequest.BodyPublishers.ofByteArray(payload)).build();
    }

    private static String prefix(AuthSpec a) {
        return a.tokenPrefix() == null || a.tokenPrefix().isBlank() ? "Bearer" : a.tokenPrefix().trim();
    }

    // ----- pre-autenticacao (bearer com usuario e senha) -----

    private String token(String tenant, IntegrationRuntime rt, boolean forceRefresh) throws TokenException {
        AuthSpec a = rt.auth();
        String key = tenant + ":" + rt.id() + ":" + fingerprint(a);
        long now = System.currentTimeMillis();
        if (!forceRefresh) {
            CachedToken c = tokens.get(key);
            if (c != null && c.expiresAtMs() > now) return c.value();
        }
        CachedToken fresh = fetchToken(rt);
        tokens.put(key, fresh);
        return fresh.value();
    }

    private CachedToken fetchToken(IntegrationRuntime rt) throws TokenException {
        AuthSpec a = rt.auth();
        URI uri;
        try {
            uri = URI.create(a.tokenUrl().trim());
        } catch (RuntimeException e) {
            throw new TokenException("URL de pré-autenticação inválida.", false);
        }
        String blocked = guard.validateForSend(uri);
        if (blocked != null) throw new TokenException("Pré-autenticação: " + blocked, false);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(or(a.usernameField(), "username"), Objects.toString(a.username(), ""));
        fields.put(or(a.passwordField(), "password"), Objects.toString(a.password(), ""));
        if (a.tokenExtra() != null) {
            for (NameValue nv : a.tokenExtra()) {
                if (nv.name() != null && !nv.name().isBlank()) fields.put(nv.name().trim(), Objects.toString(nv.value(), ""));
            }
        }
        boolean form = "form".equals(a.tokenBodyFormat());
        String body;
        try {
            body = form ? urlEncode(fields) : mapper.writeValueAsString(fields);
        } catch (IOException e) {
            throw new TokenException("Não foi possível montar a pré-autenticação.", false);
        }

        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(Math.max(1, rt.timeoutSeconds())))
                    .setHeader("Content-Type", form
                            ? "application/x-www-form-urlencoded; charset=utf-8"
                            : "application/json; charset=utf-8")
                    .setHeader("Accept", "application/json")
                    .setHeader("User-Agent", "Harmonis-Webhook/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            String text = readLimited(resp.body());
            int code = resp.statusCode();
            if (code < 200 || code >= 300) {
                throw new TokenException("Pré-autenticação falhou (HTTP " + code + ").", code == 429 || code >= 500);
            }
            JsonNode root;
            try {
                root = mapper.readTree(text);
            } catch (IOException e) {
                throw new TokenException("A resposta da pré-autenticação não é um JSON válido.", false);
            }
            String tokenPath = or(a.tokenPath(), "access_token");
            JsonNode tokenNode = at(root, tokenPath);
            if (tokenNode == null || !tokenNode.isValueNode() || tokenNode.asText().isBlank()) {
                throw new TokenException("A resposta da pré-autenticação não contém o campo \"" + tokenPath + "\".", false);
            }
            long ttl = a.tokenTtlSeconds() == null ? 3000 : a.tokenTtlSeconds();
            JsonNode exp = at(root, or(a.expiresInPath(), "expires_in"));
            if (exp != null && exp.canConvertToLong() && exp.asLong() > 0) ttl = exp.asLong();
            long effective = Math.max(5, ttl - TOKEN_SKEW_SECONDS);
            return new CachedToken(tokenNode.asText(), System.currentTimeMillis() + effective * 1000);
        } catch (TokenException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new TokenException("Pré-autenticação: tempo esgotado.", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenException("Pré-autenticação interrompida.", false);
        } catch (IOException e) {
            throw new TokenException("Pré-autenticação: falha de rede (" + brief(e) + ").", true);
        }
    }

    private static JsonNode at(JsonNode root, String dotted) {
        JsonNode cur = root;
        for (String part : dotted.split("\\.")) {
            if (cur == null || cur.isMissingNode() || cur.isNull()) return null;
            cur = cur.get(part);
        }
        return cur;
    }

    private static String fingerprint(AuthSpec a) {
        String raw = String.join("\u0001", Objects.toString(a.tokenUrl(), ""), Objects.toString(a.username(), ""),
                Objects.toString(a.password(), ""), Objects.toString(a.usernameField(), ""),
                Objects.toString(a.passwordField(), ""), Objects.toString(a.tokenBodyFormat(), ""),
                String.valueOf(a.tokenExtra()), Objects.toString(a.tokenPath(), ""));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ----- utilitarios -----

    static String hmacHex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static URI withQuery(URI uri, String name, String value) {
        String q = uri.getRawQuery();
        String pair = URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8);
        String s = uri.toString();
        return URI.create(s + (q == null || q.isEmpty() ? (s.contains("?") ? "" : "?") : "&") + pair);
    }

    /** Mascara valores de parametros sensiveis (e o parametro da api key) para o log. */
    static String maskedUrl(String url, String apiKeyParam) {
        if (url == null) return null;
        int i = url.indexOf('?');
        StringBuilder out = new StringBuilder(i < 0 ? url : url.substring(0, i));
        List<String> pairs = new ArrayList<>();
        if (i >= 0) pairs.addAll(Arrays.asList(url.substring(i + 1).split("&")));
        if (apiKeyParam != null) pairs.add(apiKeyParam + "=");
        char sep = '?';
        for (String p : pairs) {
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            String name = eq < 0 ? p : p.substring(0, eq);
            boolean sensitive = SENSITIVE_QUERY.contains(name.toLowerCase(Locale.ROOT))
                    || (apiKeyParam != null && name.equalsIgnoreCase(apiKeyParam));
            out.append(sep).append(name);
            if (eq >= 0 || sensitive) out.append('=').append(sensitive ? "***" : p.substring(eq + 1));
            sep = '&';
        }
        return out.toString();
    }

    private static String urlEncode(Map<String, String> fields) {
        StringJoiner j = new StringJoiner("&");
        fields.forEach((k, v) -> j.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return j.toString();
    }

    private static String readLimited(InputStream in) throws IOException {
        try (in) {
            byte[] buf = in.readNBytes(MAX_READ_BYTES);
            String s = new String(buf, StandardCharsets.UTF_8);
            return s.length() > MAX_STORED_RESPONSE_CHARS ? s.substring(0, MAX_STORED_RESPONSE_CHARS) + "…" : s;
        }
    }

    private static String or(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static String brief(Exception e) {
        String m = e.getMessage();
        String s = (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private static Result result(boolean ok, Integer status, String body, String error,
                                 long t0Nanos, String logUrl, boolean retryable) {
        int ms = (int) ((System.nanoTime() - t0Nanos) / 1_000_000L);
        return new Result(ok, status, body, error, ms, logUrl, retryable);
    }
}
