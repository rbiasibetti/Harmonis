package app.harmonis.forms;

import app.harmonis.documents.DocumentStorage;
import app.harmonis.forms.FormDtos.AttachmentRef;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.*;

/**
 * Upload, confirmacao (commit) e download dos anexos de formulario.
 * O tipo de arquivo aceito e o tamanho maximo vem da validacao do proprio campo.
 */
@Service
public class FormAttachmentService {

    private final FormRepository forms;
    private final FormAttachmentRepository attachments;
    private final FormSchemaService schema;
    private final DocumentStorage storage;

    public FormAttachmentService(FormRepository forms, FormAttachmentRepository attachments,
                                 FormSchemaService schema, DocumentStorage storage) {
        this.forms = forms;
        this.attachments = attachments;
        this.schema = schema;
        this.storage = storage;
    }

    public record Downloadable(Path path, String originalName, String mimeType) {}

    /** Recebe um arquivo para um campo de anexo, aplica os limites do campo e o guarda (ainda sem submissao). */
    @Transactional
    public AttachmentRef upload(long formId, String fieldKey, MultipartFile file, Long userId) {
        Form form = forms.findById(formId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formulário não encontrado."));
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo vazio.");
        }
        FormField field = schema.readFields(form.getSchemaJson()).stream()
                .filter(f -> fieldKey != null && fieldKey.equals(f.key()) && FieldTypes.isAnyFile(f.type()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo de anexo inválido."));

        FormField.Validation v = field.validation();
        enforceSize(file, v);
        enforceAccept(file, v);

        DocumentStorage.Stored stored = storage.store(file);
        FormAttachment att = new FormAttachment();
        att.setFormId(formId);
        att.setFieldKey(fieldKey);
        att.setStoredPath(stored.path());
        att.setOriginalName(stored.originalName());
        att.setMimeType(stored.mimeType());
        att.setSizeBytes(stored.size());
        att.setUploadedBy(userId);
        attachments.save(att);
        return toRef(att);
    }

    /**
     * Confirma os anexos referenciados nos campos de arquivo informados: valida a posse,
     * vincula a submissao e reescreve o valor do campo com metadados autoritativos (do banco),
     * ignorando o que o cliente enviou alem do id. Muta e retorna o proprio mapa de dados.
     */
    @Transactional
    public Map<String, Object> commit(Form form, long submissionId, Map<String, Object> data,
                                      List<FormField> fileFields, Long userId) {
        for (FormField f : fileFields) {
            if (!FieldTypes.isAnyFile(f.type())) continue;
            Object value = data.get(f.key());
            List<Long> ids = extractIds(value);
            List<AttachmentRef> refs = new ArrayList<>();
            for (Long id : ids) {
                FormAttachment att = attachments.findById(id)
                        .orElseThrow(() -> badRef(f));
                boolean ok = Objects.equals(att.getFormId(), form.getId())
                        && (att.getSubmissionId() == null || Objects.equals(att.getSubmissionId(), submissionId))
                        && (att.getUploadedBy() == null || Objects.equals(att.getUploadedBy(), userId));
                if (!ok) throw badRef(f);
                att.setSubmissionId(submissionId);
                att.setFieldKey(f.key());
                attachments.save(att);
                refs.add(toRef(att));
            }
            if (FieldTypes.isMultiFile(f.type())) {
                data.put(f.key(), refs.stream().map(FormAttachmentService::refMap).toList());
            } else {
                data.put(f.key(), refs.isEmpty() ? null : refMap(refs.get(0)));
            }
        }
        return data;
    }

    @Transactional(readOnly = true)
    public Downloadable download(long attachmentId) {
        FormAttachment att = attachments.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anexo não encontrado."));
        return new Downloadable(storage.resolve(att.getStoredPath()), att.getOriginalName(), att.getMimeType());
    }

    // ----- limites do campo -----

    private static void enforceSize(MultipartFile file, FormField.Validation v) {
        if (v == null || v.maxSizeMb() == null) return;
        long max = Math.round(v.maxSizeMb() * 1024 * 1024);
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Arquivo excede o tamanho máximo de " + trim(v.maxSizeMb()) + " MB.");
        }
    }

    private static void enforceAccept(MultipartFile file, FormField.Validation v) {
        if (v == null || v.accept() == null || v.accept().isBlank()) return;
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        for (String raw : v.accept().split(",")) {
            String token = raw.trim().toLowerCase();
            if (token.isEmpty()) continue;
            if (token.startsWith(".")) {
                if (name.endsWith(token)) return;
            } else if (token.endsWith("/*")) {
                if (mime.startsWith(token.substring(0, token.length() - 1))) return; // "image/" prefix
            } else if (token.equals(mime)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tipo de arquivo não permitido. Aceitos: " + v.accept() + ".");
    }

    // ----- helpers -----

    private static ResponseStatusException badRef(FormField f) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Anexo inválido no campo \"" + f.label() + "\".");
    }

    private static AttachmentRef toRef(FormAttachment a) {
        return new AttachmentRef(a.getId(), a.getOriginalName(), a.getSizeBytes(), a.getMimeType());
    }

    private static Map<String, Object> refMap(AttachmentRef r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("name", r.name());
        m.put("size", r.size());
        m.put("mime", r.mime());
        return m;
    }

    /** Extrai os ids de anexo de um valor de campo (objeto, lista de objetos, numero ou string). */
    @SuppressWarnings("unchecked")
    private static List<Long> extractIds(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            List<Long> ids = new ArrayList<>();
            for (Object o : list) {
                Long id = oneId(o);
                if (id != null) ids.add(id);
            }
            return ids;
        }
        Long id = oneId(value);
        return id == null ? List.of() : List.of(id);
    }

    private static Long oneId(Object o) {
        if (o == null) return null;
        if (o instanceof Map<?, ?> m) return toLong(m.get("id"));
        return toLong(o);
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString().trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
