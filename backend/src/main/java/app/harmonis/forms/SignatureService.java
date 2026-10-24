package app.harmonis.forms;

import app.harmonis.documents.DocumentStorage;
import app.harmonis.forms.FormDtos.GeoRef;
import app.harmonis.forms.FormDtos.SignatureRef;
import app.harmonis.forms.FormDtos.SignerRef;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Captura, confirmacao (commit) e leitura de assinaturas. A imagem (desenho ou selfie) e
 * guardada em disco; os metadados do ato de assinar (signatario, data/hora, IP, dispositivo
 * e geolocalizacao) ficam no banco. Dados confiaveis (signatario, carimbo, IP, User-Agent)
 * sao resolvidos no servidor; a geolocalizacao vem do cliente (best-effort).
 */
@Service
public class SignatureService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> STYLES = Set.of("drawn", "selfie");

    private final FormRepository forms;
    private final SignatureRepository signatures;
    private final FormSchemaService schema;
    private final DocumentStorage storage;
    private final UserRepository users;

    public SignatureService(FormRepository forms, SignatureRepository signatures,
                            FormSchemaService schema, DocumentStorage storage, UserRepository users) {
        this.forms = forms;
        this.signatures = signatures;
        this.schema = schema;
        this.storage = storage;
        this.users = users;
    }

    public record Downloadable(Path path, String mimeType) {}

    /** Metadados de geolocalizacao enviados pelo cliente (best-effort). */
    public record ClientGeo(String status, Double lat, Double lon, Double accuracy) {}

    /**
     * Captura uma assinatura: valida o campo e o estilo, guarda a imagem e registra os metadados
     * (os confiaveis vem do servidor). Ainda sem submissao; vinculada no envio.
     */
    @Transactional
    public SignatureRef capture(long formId, String fieldKey, String style, MultipartFile image,
                                ClientGeo geo, HttpServletRequest http, Long userId) {
        Form form = forms.findById(formId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formulário não encontrado."));
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem da assinatura vazia.");
        }
        if (image.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A imagem da assinatura é muito grande.");
        }
        String mime = image.getContentType() == null ? "" : image.getContentType().toLowerCase();
        if (!mime.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A assinatura precisa ser uma imagem.");
        }
        if (style == null || !STYLES.contains(style)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estilo de assinatura inválido.");
        }

        FormField field = schema.readFields(form.getSchemaJson()).stream()
                .filter(f -> fieldKey != null && fieldKey.equals(f.key()) && FieldTypes.isSignature(f.type()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo de assinatura inválido."));
        String allowed = field.display(); // drawn | selfie | both
        if (!"both".equals(allowed) && !allowed.equals(style)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este campo exige assinatura do tipo " + styleLabel(allowed) + ".");
        }

        User signer = userId == null ? null : users.findById(userId).orElse(null);
        DocumentStorage.Stored stored = storage.store(image);

        Signature sig = new Signature();
        sig.setFormId(formId);
        sig.setFieldKey(fieldKey);
        sig.setStyle(style);
        sig.setStoredPath(stored.path());
        sig.setMimeType(stored.mimeType());
        sig.setSizeBytes(stored.size());
        sig.setSignerUserId(userId);
        sig.setSignerName(signer == null ? null : signer.getName());
        sig.setSignerEmail(signer == null ? null : signer.getEmail());
        sig.setSignedAt(Instant.now());
        sig.setIpAddress(clientIp(http));
        sig.setUserAgent(trim(http == null ? null : http.getHeader("User-Agent"), 500));
        applyGeo(sig, geo);
        signatures.save(sig);
        return toRef(sig);
    }

    /**
     * Confirma as assinaturas referenciadas nos campos informados: valida a posse, vincula a
     * submissao e reescreve o valor do campo com os metadados autoritativos (do banco).
     */
    @Transactional
    public Map<String, Object> commit(Form form, long submissionId, Map<String, Object> data,
                                      List<FormField> signatureFields, Long userId) {
        for (FormField f : signatureFields) {
            if (!FieldTypes.isSignature(f.type())) continue;
            Long id = FormSchemaService.signatureId(data.get(f.key()));
            if (id == null) { data.put(f.key(), null); continue; }
            Signature sig = signatures.findById(id)
                    .orElseThrow(() -> badRef(f));
            boolean ok = Objects.equals(sig.getFormId(), form.getId())
                    && (sig.getSubmissionId() == null || Objects.equals(sig.getSubmissionId(), submissionId))
                    && (sig.getSignerUserId() == null || Objects.equals(sig.getSignerUserId(), userId));
            if (!ok) throw badRef(f);
            sig.setSubmissionId(submissionId);
            sig.setFieldKey(f.key());
            signatures.save(sig);
            data.put(f.key(), valueMap(sig));
        }
        return data;
    }

    @Transactional(readOnly = true)
    public List<Signature> forSubmission(long submissionId) {
        return signatures.findBySubmissionIdOrderBySignedAtAsc(submissionId);
    }

    @Transactional(readOnly = true)
    public Downloadable image(long signatureId) {
        Signature sig = signatures.findById(signatureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assinatura não encontrada."));
        return new Downloadable(storage.resolve(sig.getStoredPath()), sig.getMimeType());
    }

    /** Remove as imagens e os registros de todas as assinaturas de um formulario. */
    @Transactional
    public void deleteAllForForm(long formId) {
        List<Signature> all = signatures.findByFormId(formId);
        if (all.isEmpty()) return;
        all.forEach(s -> storage.delete(s.getStoredPath()));
        signatures.deleteAll(all);
    }

    // ----- helpers -----

    /** Valor gravado no data_json do campo (metadados visiveis na API/webhook, sem a imagem). */
    public static Map<String, Object> valueMap(Signature s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("signatureId", s.getId());
        m.put("style", s.getStyle());
        m.put("signedAt", s.getSignedAt() == null ? null : s.getSignedAt().toString());
        Map<String, Object> signer = new LinkedHashMap<>();
        signer.put("id", s.getSignerUserId());
        signer.put("name", s.getSignerName());
        signer.put("email", s.getSignerEmail());
        m.put("signer", signer);
        m.put("ip", s.getIpAddress());
        m.put("device", s.getUserAgent());
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("status", s.getGeoStatus());
        geo.put("lat", s.getGeoLat());
        geo.put("lon", s.getGeoLon());
        geo.put("accuracy", s.getGeoAccuracy());
        m.put("geo", geo);
        return m;
    }

    private static SignatureRef toRef(Signature s) {
        return new SignatureRef(s.getId(), s.getStyle(),
                s.getSignedAt() == null ? null : s.getSignedAt().toString(),
                new SignerRef(s.getSignerUserId(), s.getSignerName(), s.getSignerEmail()),
                s.getIpAddress(), s.getUserAgent(),
                new GeoRef(s.getGeoStatus(), s.getGeoLat(), s.getGeoLon(), s.getGeoAccuracy()));
    }

    private static void applyGeo(Signature sig, ClientGeo geo) {
        String status = geo == null || geo.status() == null ? "unknown" : geo.status().trim().toLowerCase();
        if (!Set.of("ok", "denied", "unavailable", "unknown").contains(status)) status = "unknown";
        if ("ok".equals(status) && (geo.lat() == null || geo.lon() == null)) status = "unavailable";
        sig.setGeoStatus(status);
        if ("ok".equals(status)) {
            sig.setGeoLat(geo.lat());
            sig.setGeoLon(geo.lon());
            sig.setGeoAccuracy(geo.accuracy());
        }
    }

    private static String clientIp(HttpServletRequest http) {
        if (http == null) return null;
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return trim(fwd.split(",")[0].trim(), 64);
        return trim(http.getRemoteAddr(), 64);
    }

    private static ResponseStatusException badRef(FormField f) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Assinatura inválida no campo \"" + f.label() + "\".");
    }

    private static String styleLabel(String style) {
        return "drawn".equals(style) ? "desenhada" : "selfie";
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
