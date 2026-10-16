package app.harmonis.integrations;

import app.harmonis.integrations.IntegrationDtos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Cadastro de integracoes (webhooks de saida): validacao, cifragem/mescla de segredos,
 * publicacao e montagem da configuracao decifrada usada no envio. Segredos NUNCA saem nas views.
 */
@Service
public class IntegrationService {

    static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH");
    static final Set<String> AUTH_TYPES =
            Set.of("none", "basic", "apikey", "bearer", "bearer_preauth", "headers");
    private static final Pattern TOKEN = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "host", "content-length", "content-type", "transfer-encoding", "connection", "upgrade",
            "expect", "te", "trailer", "keep-alive", "proxy-authorization", "proxy-connection");

    private final IntegrationRepository repo;
    private final FormHookRepository hooks;
    private final IntegrationLogRepository logs;
    private final SecretCipher cipher;
    private final UrlGuard guard;
    private final WebhookSender sender;
    private final ObjectMapper mapper;

    public IntegrationService(IntegrationRepository repo, FormHookRepository hooks, IntegrationLogRepository logs,
                              SecretCipher cipher, UrlGuard guard, WebhookSender sender, ObjectMapper mapper) {
        this.repo = repo;
        this.hooks = hooks;
        this.logs = logs;
        this.cipher = cipher;
        this.guard = guard;
        this.sender = sender;
        this.mapper = mapper;
    }

    // ----- consultas -----

    @Transactional(readOnly = true)
    public List<IntegrationSummary> list() {
        return repo.findAllByOrderByNameAsc().stream().map(i -> {
            IntegrationLog last = logs.findFirstByIntegrationIdOrderByCreatedAtDescIdDesc(i.getId()).orElse(null);
            return new IntegrationSummary(i.getId(), i.getName(), i.getDescription(), i.getUrl(), i.getMethod(),
                    i.getAuthType(), i.getStatus(), hooks.countByIntegrationId(i.getId()),
                    last == null ? null : last.getStatus(), last == null ? null : last.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<IntegrationOption> options() {
        return repo.findByStatusOrderByNameAsc(Integration.PUBLISHED).stream()
                .map(i -> new IntegrationOption(i.getId(), i.getName(), i.getDescription())).toList();
    }

    @Transactional(readOnly = true)
    public IntegrationView get(long id) {
        return toView(require(id));
    }

    @Transactional(readOnly = true)
    public Optional<Integration> find(Long id) {
        return id == null ? Optional.empty() : repo.findById(id);
    }

    Integration require(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integração não encontrada."));
    }

    // ----- escrita -----

    @Transactional
    public IntegrationView create(SaveIntegrationRequest req, Long userId) {
        Integration i = new Integration();
        apply(i, req, true);
        i.setCreatedBy(userId);
        i.setStatus(Integration.DRAFT);
        repo.save(i);
        return toView(i);
    }

    @Transactional
    public IntegrationView update(long id, SaveIntegrationRequest req) {
        Integration i = require(id);
        apply(i, req, false);
        repo.save(i);
        sender.invalidateTokens(id);
        return toView(i);
    }

    @Transactional
    public IntegrationView publish(long id) {
        Integration i = require(id);
        String blocked = guard.validateSyntax(i.getUrl());
        if (blocked != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, blocked);
        }
        i.setStatus(Integration.PUBLISHED);
        i.setPublishedAt(Instant.now());
        repo.save(i);
        return toView(i);
    }

    @Transactional
    public IntegrationView unpublish(long id) {
        Integration i = require(id);
        i.setStatus(Integration.DRAFT);
        repo.save(i);
        return toView(i);
    }

    @Transactional
    public void delete(long id) {
        Integration i = require(id);
        if (hooks.countByIntegrationId(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A integração está vinculada a formulários. Remova os vínculos no fluxo de cada formulário antes de excluir.");
        }
        repo.delete(i);
        sender.invalidateTokens(id);
    }

    // ----- configuracao de envio (decifrada, apenas em memoria) -----

    public IntegrationRuntime runtime(Integration i) {
        AuthSpec stored = readAuth(i.getAuthJson());
        AuthSpec auth = new AuthSpec(stored.type(), stored.username(), cipher.decrypt(stored.password()),
                stored.keyName(), cipher.decrypt(stored.keyValue()), stored.keyIn(),
                cipher.decrypt(stored.token()), stored.tokenPrefix(),
                stored.tokenUrl(), stored.tokenBodyFormat(), stored.usernameField(), stored.passwordField(),
                stored.tokenExtra(), stored.tokenPath(), stored.expiresInPath(), stored.tokenTtlSeconds());
        List<HeaderSpec> headers = readHeaders(i.getHeadersJson()).stream()
                .map(h -> new HeaderSpec(h.name(), h.secret() ? cipher.decrypt(h.value()) : h.value(), h.secret()))
                .toList();
        return new IntegrationRuntime(i.getId(), i.getUrl(), i.getMethod(), auth, headers,
                cipher.decrypt(i.getSigningSecret()), i.getTimeoutSeconds());
    }

    // ----- validacao / normalizacao -----

    private void apply(Integration i, SaveIntegrationRequest req, boolean isNew) {
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty() || name.length() > 150) {
            throw bad("Informe um nome de até 150 caracteres.");
        }
        boolean dup = isNew ? repo.existsByNameIgnoreCase(name) : repo.existsByNameIgnoreCaseAndIdNot(name, i.getId());
        if (dup) throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma integração com esse nome.");

        String url = req.url() == null ? "" : req.url().trim();
        if (url.length() > 1000) throw bad("A URL é longa demais (máx. 1000 caracteres).");
        String urlProblem = guard.validateSyntax(url);
        if (urlProblem != null) throw bad(urlProblem);

        String method = req.method() == null || req.method().isBlank() ? "POST" : req.method().trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) throw bad("Método inválido. Use POST, PUT ou PATCH.");

        String description = req.description() == null || req.description().isBlank() ? null : req.description().trim();
        if (description != null && description.length() > 500) throw bad("A descrição excede 500 caracteres.");

        AuthSpec existingAuth = isNew ? null : readAuth(i.getAuthJson());
        AuthSpec auth = normalizeAuth(req.auth(), existingAuth);
        List<HeaderSpec> headers = normalizeHeaders(req.headers(),
                isNew ? List.of() : readHeaders(i.getHeadersJson()), auth.type());

        String signing = i.getSigningSecret();
        if (Boolean.TRUE.equals(req.clearSigningSecret())) signing = null;
        else if (req.signingSecret() != null && !req.signingSecret().isBlank()) signing = cipher.encrypt(req.signingSecret());

        int timeout = req.timeoutSeconds() == null ? 15 : req.timeoutSeconds();
        if (timeout < 1 || timeout > 60) throw bad("O tempo limite deve estar entre 1 e 60 segundos.");
        int retries = req.retries() == null ? 2 : req.retries();
        if (retries < 0 || retries > 5) throw bad("As retentativas devem estar entre 0 e 5.");

        i.setName(name);
        i.setDescription(description);
        i.setUrl(url);
        i.setMethod(method);
        i.setAuthType(auth.type());
        i.setAuthJson(write(auth));
        i.setHeadersJson(headers.isEmpty() ? null : write(headers));
        i.setSigningSecret(signing);
        i.setTimeoutSeconds(timeout);
        i.setRetries(retries);
    }

    /** Devolve a autenticacao normalizada, so com os campos do tipo escolhido e segredos CIFRADOS. */
    AuthSpec normalizeAuth(AuthSpec in, AuthSpec existing) {
        String type = in.type() == null ? "none" : in.type().trim().toLowerCase(Locale.ROOT);
        if (!AUTH_TYPES.contains(type)) throw bad("Tipo de autenticação inválido.");
        boolean same = existing != null && type.equals(existing.type());

        switch (type) {
            case "basic": {
                String user = required(in.username(), "o usuário");
                String pass = secret(in.password(), same ? existing.password() : null, "a senha");
                return new AuthSpec(type, user, pass, null, null, null, null, null,
                        null, null, null, null, null, null, null, null);
            }
            case "apikey": {
                String keyIn = in.keyIn() == null || in.keyIn().isBlank() ? "header" : in.keyIn().trim().toLowerCase(Locale.ROOT);
                if (!keyIn.equals("header") && !keyIn.equals("query")) throw bad("A chave deve ir no cabeçalho ou na query.");
                String keyName = required(in.keyName(), "o nome da chave");
                if (keyIn.equals("header")) {
                    checkHeaderName(keyName);
                } else if (!keyName.matches("[A-Za-z0-9_.\\-]{1,60}")) {
                    throw bad("Nome de parâmetro inválido para a api key.");
                }
                String value = secret(in.keyValue(), same ? existing.keyValue() : null, "o valor da chave");
                return new AuthSpec(type, null, null, keyName, value, keyIn, null, null,
                        null, null, null, null, null, null, null, null);
            }
            case "bearer": {
                String token = secret(in.token(), same ? existing.token() : null, "o token");
                return new AuthSpec(type, null, null, null, null, null, token, cleanPrefix(in.tokenPrefix()),
                        null, null, null, null, null, null, null, null);
            }
            case "bearer_preauth": {
                String tokenUrl = required(in.tokenUrl(), "a URL de pré-autenticação");
                String problem = guard.validateSyntax(tokenUrl);
                if (problem != null) throw bad("Pré-autenticação: " + problem);
                String user = required(in.username(), "o usuário da pré-autenticação");
                String pass = secret(in.password(), same ? existing.password() : null, "a senha da pré-autenticação");
                String format = "form".equalsIgnoreCase(in.tokenBodyFormat()) ? "form" : "json";
                List<NameValue> extra = new ArrayList<>();
                if (in.tokenExtra() != null) {
                    if (in.tokenExtra().size() > 10) throw bad("No máximo 10 campos extras na pré-autenticação.");
                    for (NameValue nv : in.tokenExtra()) {
                        if (nv.name() == null || nv.name().isBlank()) continue;
                        extra.add(new NameValue(nv.name().trim(), nv.value() == null ? "" : nv.value()));
                    }
                }
                int ttl = in.tokenTtlSeconds() == null ? 3000 : in.tokenTtlSeconds();
                if (ttl < 30 || ttl > 86400) throw bad("A validade padrão do token deve estar entre 30 e 86400 segundos.");
                return new AuthSpec(type, user, pass, null, null, null, null, cleanPrefix(in.tokenPrefix()),
                        tokenUrl, format, orDefault(in.usernameField(), "username"),
                        orDefault(in.passwordField(), "password"), extra,
                        orDefault(in.tokenPath(), "access_token"), orDefault(in.expiresInPath(), "expires_in"), ttl);
            }
            default:
                return new AuthSpec(type, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null);
        }
    }

    /** Valida cabecalhos e cifra os marcados como secretos (valor vazio mantem o atual de mesmo nome). */
    List<HeaderSpec> normalizeHeaders(List<HeaderSpec> in, List<HeaderSpec> existing, String authType) {
        List<HeaderSpec> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (in != null) {
            if (in.size() > 20) throw bad("No máximo 20 cabeçalhos customizados.");
            for (HeaderSpec h : in) {
                String name = h.name() == null ? "" : h.name().trim();
                if (name.isEmpty()) continue;
                checkHeaderName(name);
                String lower = name.toLowerCase(Locale.ROOT);
                if (!seen.add(lower)) throw bad("Cabeçalho repetido: " + name);
                if (lower.equals("authorization") && !authType.equals("none") && !authType.equals("headers")) {
                    throw bad("O cabeçalho Authorization já é gerenciado pelo tipo de autenticação escolhido.");
                }
                String value = h.value();
                if (h.secret()) {
                    String keep = existing.stream()
                            .filter(e -> e.secret() && e.name().equalsIgnoreCase(name))
                            .map(HeaderSpec::value).findFirst().orElse(null);
                    out.add(new HeaderSpec(name, secret(value, keep, "o valor do cabeçalho " + name), true));
                } else {
                    if (value == null || value.isBlank()) throw bad("Informe o valor do cabeçalho " + name + ".");
                    if (value.contains("\r") || value.contains("\n")) throw bad("Valor inválido no cabeçalho " + name + ".");
                    if (value.length() > 2000) throw bad("Valor longo demais no cabeçalho " + name + ".");
                    out.add(new HeaderSpec(name, value, false));
                }
            }
        }
        if (authType.equals("headers") && out.isEmpty()) {
            throw bad("Informe ao menos um cabeçalho customizado.");
        }
        return out;
    }

    private static void checkHeaderName(String name) {
        if (name.length() > 100 || !TOKEN.matcher(name).matches()) throw bad("Nome de cabeçalho inválido: " + name);
        String lower = name.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_HEADERS.contains(lower) || lower.startsWith("x-harmonis-")) {
            throw bad("O cabeçalho \"" + name + "\" é reservado e não pode ser definido.");
        }
    }

    private String secret(String incoming, String existingEncrypted, String label) {
        if (incoming != null && !incoming.isBlank()) {
            if (incoming.length() > 4000) throw bad("Valor longo demais para " + label + ".");
            if (incoming.contains("\r") || incoming.contains("\n")) throw bad("Valor inválido para " + label + ".");
            return cipher.encrypt(incoming);
        }
        if (existingEncrypted != null && !existingEncrypted.isBlank()) return existingEncrypted;
        throw bad("Informe " + label + ".");
    }

    private static String required(String v, String label) {
        if (v == null || v.isBlank()) throw bad("Informe " + label + ".");
        return v.trim();
    }

    private static String orDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v.trim();
    }

    private static String cleanPrefix(String p) {
        String v = orDefault(p, "Bearer");
        if (v.length() > 30 || !TOKEN.matcher(v).matches()) throw bad("Prefixo de token inválido.");
        return v;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    // ----- mapeamento -----

    private IntegrationView toView(Integration i) {
        AuthSpec a = readAuth(i.getAuthJson());
        AuthView av = new AuthView(a.type(), a.username(), notBlank(a.password()),
                a.keyName(), notBlank(a.keyValue()), a.keyIn(), notBlank(a.token()), a.tokenPrefix(),
                a.tokenUrl(), a.tokenBodyFormat(), a.usernameField(), a.passwordField(),
                a.tokenExtra(), a.tokenPath(), a.expiresInPath(), a.tokenTtlSeconds());
        List<HeaderView> hv = readHeaders(i.getHeadersJson()).stream()
                .map(h -> new HeaderView(h.name(), h.secret() ? null : h.value(), h.secret(), notBlank(h.value())))
                .toList();
        return new IntegrationView(i.getId(), i.getName(), i.getDescription(), i.getUrl(), i.getMethod(), av, hv,
                notBlank(i.getSigningSecret()), i.getTimeoutSeconds(), i.getRetries(), i.getStatus(),
                i.getPublishedAt(), hooks.countByIntegrationId(i.getId()), i.getUpdatedAt());
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    AuthSpec readAuth(String json) {
        if (json == null || json.isBlank()) return AuthSpec.none();
        try { return mapper.readValue(json, AuthSpec.class); }
        catch (Exception e) { return AuthSpec.none(); }
    }

    List<HeaderSpec> readHeaders(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return mapper.readValue(json, new TypeReference<List<HeaderSpec>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private String write(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { throw bad("Configuração inválida."); }
    }
}
