package app.harmonis.forms;

import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import app.harmonis.workflow.ProcessInstance;
import app.harmonis.workflow.ProcessInstanceRepository;
import app.harmonis.workflow.ProcessTask;
import app.harmonis.workflow.ProcessTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Ficha em PDF de um registro: pares rotulo/valor dispostos como no layout do formulario
 * (grade de 12 colunas; largura full/half/third; blocos viram titulos de secao) e,
 * quando ha processo, o historico de aprovacao.
 */
@Service
public class SubmissionPdfService {

    public record Pdf(byte[] bytes, String filename) {}

    public static org.springframework.http.ResponseEntity<byte[]> response(Pdf pdf) {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdf.filename() + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf.bytes());
    }

    /** Textos e formatos do PDF por idioma (pt-BR | en | es). */
    static final class Tx {
        final String record, sentOn, by, status, history, noSteps, noFields, issuedOn, page, yes, no, rolePrefix, user;
        final String[] cols;
        final java.util.Map<String, String> states, taskStates;
        final DateTimeFormatter dt, d;

        private Tx(String record, String sentOn, String by, String status, String history, String noSteps,
                   String noFields, String issuedOn, String page, String yes, String no, String rolePrefix,
                   String user, String[] cols, java.util.Map<String, String> states,
                   java.util.Map<String, String> taskStates, String dtPattern, String dPattern) {
            this.record = record; this.sentOn = sentOn; this.by = by; this.status = status; this.history = history;
            this.noSteps = noSteps; this.noFields = noFields; this.issuedOn = issuedOn; this.page = page;
            this.yes = yes; this.no = no; this.rolePrefix = rolePrefix; this.user = user; this.cols = cols;
            this.states = states; this.taskStates = taskStates;
            this.dt = DateTimeFormatter.ofPattern(dtPattern); this.d = DateTimeFormatter.ofPattern(dPattern);
        }

        static Tx of(String lang) {
            String l = app.harmonis.identity.Languages.normalize(lang);
            if ("en".equals(l)) {
                return new Tx("Record no. ", "Submitted on ", "by ", "Status: ", "Approval history",
                        "No approval steps were required.", "This form has no fields.", "Issued on ", "Page ",
                        "Yes", "No", "Role: ", "user",
                        new String[]{"STEP", "APPROVER", "RESULT", "DECIDED BY / ON", "COMMENT"},
                        java.util.Map.of("approved", "Approved", "rejected", "Rejected", "pending", "In progress"),
                        java.util.Map.of("approved", "Approved", "rejected", "Rejected", "pending", "Pending", "skipped", "Skipped"),
                        "MM/dd/yyyy hh:mm a", "MM/dd/yyyy");
            }
            if ("es".equals(l)) {
                return new Tx("Registro n.º ", "Enviado el ", "por ", "Estado: ", "Historial de aprobación",
                        "No fue necesaria ninguna etapa de aprobación.", "Este formulario no tiene campos.", "Emitido el ", "Página ",
                        "Sí", "No", "Rol: ", "usuario",
                        new String[]{"ETAPA", "APROBADOR", "RESULTADO", "DECIDIDO POR / EL", "COMENTARIO"},
                        java.util.Map.of("approved", "Aprobado", "rejected", "Rechazado", "pending", "En curso"),
                        java.util.Map.of("approved", "Aprobado", "rejected", "Rechazado", "pending", "Pendiente", "skipped", "Omitido"),
                        "dd/MM/yyyy HH:mm", "dd/MM/yyyy");
            }
            return new Tx("Registro nº ", "Enviado em ", "por ", "Situação: ", "Histórico de aprovação",
                    "Nenhuma etapa de aprovação foi necessária.", "Este formulário não possui campos.", "Emitido em ", "Página ",
                    "Sim", "Não", "Papel: ", "usuário",
                    new String[]{"ETAPA", "APROVADOR", "RESULTADO", "DECIDIDO POR / EM", "COMENTÁRIO"},
                    java.util.Map.of("approved", "Aprovado", "rejected", "Reprovado", "pending", "Em andamento"),
                    java.util.Map.of("approved", "Aprovado", "rejected", "Reprovado", "pending", "Pendente", "skipped", "Dispensado"),
                    "dd/MM/yyyy HH:mm", "dd/MM/yyyy");
        }

        String state(String k) { return states.getOrDefault(k == null ? "" : k, k); }
        String task(String k) { return taskStates.getOrDefault(k == null ? "" : k, k); }
    }

    /** Textos da secao de assinaturas por idioma. */
    static final class SigTx {
        final String signedBy, someone, historyTitle, historyLead, signature, imageUnavailable;
        final String mStyle, mSigner, mEmail, mWhen, mIp, mDevice, mLocation;
        final String drawn, selfie;
        final java.util.Map<String, String> geo;

        private SigTx(String signedBy, String someone, String historyTitle, String historyLead, String signature,
                      String imageUnavailable, String mStyle, String mSigner, String mEmail, String mWhen,
                      String mIp, String mDevice, String mLocation, String drawn, String selfie,
                      java.util.Map<String, String> geo) {
            this.signedBy = signedBy; this.someone = someone; this.historyTitle = historyTitle;
            this.historyLead = historyLead; this.signature = signature; this.imageUnavailable = imageUnavailable;
            this.mStyle = mStyle; this.mSigner = mSigner; this.mEmail = mEmail; this.mWhen = mWhen;
            this.mIp = mIp; this.mDevice = mDevice; this.mLocation = mLocation;
            this.drawn = drawn; this.selfie = selfie; this.geo = geo;
        }

        String styleName(String style) { return "selfie".equals(style) ? selfie : drawn; }
        String geoStatus(String st) { return geo.getOrDefault(st == null ? "unknown" : st, geo.get("unknown")); }

        static SigTx of(String lang) {
            String l = app.harmonis.identity.Languages.normalize(lang);
            if ("en".equals(l)) {
                return new SigTx("Signed by ", "user", "Signature history",
                        "Every signature collected for this record and the metadata captured for each one.",
                        "Signature", "Image unavailable",
                        "STYLE", "SIGNER", "E-MAIL", "DATE / TIME", "IP ADDRESS", "DEVICE", "LOCATION",
                        "Drawn", "Selfie",
                        java.util.Map.of("ok", "provided", "denied", "permission denied",
                                "unavailable", "unavailable", "unknown", "not provided"));
            }
            if ("es".equals(l)) {
                return new SigTx("Firmado por ", "usuario", "Historial de firmas",
                        "Todas las firmas recogidas para este registro y los metadatos capturados en cada una.",
                        "Firma", "Imagen no disponible",
                        "ESTILO", "FIRMANTE", "CORREO", "FECHA / HORA", "DIRECCIÓN IP", "DISPOSITIVO", "UBICACIÓN",
                        "Dibujada", "Selfie",
                        java.util.Map.of("ok", "proporcionada", "denied", "permiso denegado",
                                "unavailable", "no disponible", "unknown", "no proporcionada"));
            }
            return new SigTx("Assinado por ", "usuário", "Histórico de assinaturas",
                    "Todas as assinaturas coletadas para este registro e os metadados capturados em cada uma.",
                    "Assinatura", "Imagem indisponível",
                    "ESTILO", "SIGNATÁRIO", "E-MAIL", "DATA / HORA", "ENDEREÇO IP", "DISPOSITIVO", "LOCALIZAÇÃO",
                    "Desenhada", "Selfie",
                    java.util.Map.of("ok", "informada", "denied", "permissão negada",
                            "unavailable", "indisponível", "unknown", "não informada"));
        }
    }

    private static final Color INK = new Color(0x1f, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6b, 0x72, 0x80);
    private static final Color LINE = new Color(0xd1, 0xd5, 0xdb);
    private static final Color BAND = new Color(0xf3, 0xf4, 0xf6);
    private static final Color ACCENT = new Color(0x1d, 0x4e, 0xd8);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static final Font F_TITLE = font(18, Font.BOLD, INK);
    private static final Font F_SUB = font(10, Font.NORMAL, MUTED);
    private static final Font F_SECTION = font(11, Font.BOLD, ACCENT);
    private static final Font F_LABEL = font(7.5f, Font.BOLD, MUTED);
    private static final Font F_VALUE = font(10, Font.NORMAL, INK);
    private static final Font F_EMPTY = font(10, Font.NORMAL, MUTED);
    private static final Font F_TH = font(8, Font.BOLD, MUTED);
    private static final Font F_TD = font(9, Font.NORMAL, INK);

    private final FormRepository forms;
    private final FormSubmissionRepository submissions;
    private final ProcessInstanceRepository instances;
    private final ProcessTaskRepository tasks;
    private final UserRepository users;
    private final FormSchemaService schema;
    private final ObjectMapper mapper;
    private final SignatureService signaturesService;
    private final app.harmonis.documents.DocumentStorage storage;

    public SubmissionPdfService(FormRepository forms, FormSubmissionRepository submissions,
                                ProcessInstanceRepository instances, ProcessTaskRepository tasks,
                                UserRepository users, FormSchemaService schema, ObjectMapper mapper,
                                SignatureService signaturesService, app.harmonis.documents.DocumentStorage storage) {
        this.forms = forms;
        this.submissions = submissions;
        this.instances = instances;
        this.tasks = tasks;
        this.users = users;
        this.schema = schema;
        this.mapper = mapper;
        this.signaturesService = signaturesService;
        this.storage = storage;
    }

    /** PDF do registro; formId (opcional) garante que o registro pertence ao formulario da URL. */
    @Transactional(readOnly = true)
    public Pdf render(Long formId, long submissionId, String lang) {
        Tx tx = Tx.of(lang);
        FormSubmission sub = submissions.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resposta não encontrada."));
        if (formId != null && !formId.equals(sub.getFormId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resposta não encontrada.");
        }
        Form form = forms.findById(sub.getFormId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formulário não encontrado."));
        ProcessInstance inst = instances.findFirstBySubmissionId(sub.getId()).orElse(null);
        List<ProcessTask> taskList = inst == null ? List.of()
                : tasks.findByInstanceIdOrderByStepIndexAscCreatedAtAsc(inst.getId());

        Map<Long, String> names = new HashMap<>();
        for (User u : users.findAll()) names.put(u.getId(), u.getName());

        Map<String, Object> data = readData(sub.getDataJson());
        List<FormField> fields = schema.readFields(form.getSchemaJson());
        List<Signature> sigs = signaturesService.forSubmission(sub.getId());
        Map<Long, Signature> sigById = new HashMap<>();
        for (Signature sg : sigs) sigById.put(sg.getId(), sg);
        SigTx sx = SigTx.of(lang);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 40, 44);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer(form.getName(), sub.getId(), tx));
            doc.addTitle(form.getName() + " #" + sub.getId());
            doc.addCreator("Harmonis");
            doc.open();

            header(doc, form, sub, inst, names, tx);
            body(doc, fields, data, tx, sigById, sx);
            if (inst != null) history(doc, inst, taskList, names, tx);
            if (!sigs.isEmpty()) signaturePages(doc, sigs, fields, tx, sx);

            doc.close();
            return new Pdf(out.toByteArray(), "registro-" + (form.getSlug() == null ? form.getId() : form.getSlug())
                    + "-" + sub.getId() + ".pdf");
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar o PDF.", e);
        }
    }

    @Transactional(readOnly = true)
    public Pdf renderForProcess(long instanceId, String lang) {
        ProcessInstance inst = instances.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo não encontrado."));
        return render(null, inst.getSubmissionId(), lang);
    }

    // ---------------------------------------------------------------- secoes

    private void header(Document doc, Form form, FormSubmission sub, ProcessInstance inst,
                        Map<Long, String> names, Tx tx) throws DocumentException {
        Paragraph title = new Paragraph(form.getName(), F_TITLE);
        title.setSpacingAfter(2);
        doc.add(title);

        List<String> meta = new ArrayList<>();
        meta.add(tx.record + sub.getId());
        if (sub.getCreatedAt() != null) meta.add(tx.sentOn + tx.dt.format(sub.getCreatedAt().atZone(ZONE)));
        if (sub.getSubmittedBy() != null && names.get(sub.getSubmittedBy()) != null) {
            meta.add(tx.by + names.get(sub.getSubmittedBy()));
        }
        if (inst != null) meta.add(tx.status + tx.state(inst.getStatus()));
        Paragraph sp = new Paragraph(String.join("  ·  ", meta), F_SUB);
        sp.setSpacingAfter(8);
        doc.add(sp);

        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(" ", font(2, Font.NORMAL, INK)));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(ACCENT);
        c.setBorderWidth(1.5f);
        rule.addCell(c);
        rule.setSpacingAfter(10);
        doc.add(rule);
    }

    /** Campos na ordem do formulario, ocupando a grade de 12 colunas como na tela de preenchimento. */
    private void body(Document doc, List<FormField> fields, Map<String, Object> data, Tx tx,
                      Map<Long, Signature> sigById, SigTx sx) throws DocumentException {
        PdfPTable grid = new PdfPTable(12);
        grid.setWidthPercentage(100);
        int used = 0;
        boolean any = false;

        for (FormField f : fields) {
            if (FieldTypes.isLayout(f.type())) {
                fill(grid, used);
                used = 0;
                grid.addCell(sectionCell(f));
                any = true;
                continue;
            }
            if (f.key() == null || !schema.visible(f, data)) continue;
            int span = spanOf(f.width());
            if (used + span > 12) {
                fill(grid, used);
                used = 0;
            }
            if (FieldTypes.isSignature(f.type())) {
                grid.addCell(signatureCell(f, data.get(f.key()), span, sigById, tx, sx));
            } else {
                grid.addCell(fieldCell(f, data.get(f.key()), span, tx));
            }
            used = (used + span) % 12;
            any = true;
        }
        fill(grid, used);

        if (!any) {
            doc.add(new Paragraph(tx.noFields, F_EMPTY));
            return;
        }
        doc.add(grid);
    }

    private void history(Document doc, ProcessInstance inst, List<ProcessTask> list,
                         Map<Long, String> names, Tx tx) throws DocumentException {
        Paragraph h = new Paragraph(tx.history, F_SECTION);
        h.setSpacingBefore(14);
        h.setSpacingAfter(4);
        doc.add(h);

        if (list.isEmpty()) {
            doc.add(new Paragraph(tx.noSteps, F_EMPTY));
            return;
        }
        PdfPTable t = new PdfPTable(new float[]{3f, 3f, 2f, 3f, 4f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String col : tx.cols) {
            PdfPCell c = new PdfPCell(new Phrase(col, F_TH));
            c.setBackgroundColor(BAND);
            c.setBorderColor(LINE);
            c.setPadding(4);
            t.addCell(c);
        }
        for (ProcessTask k : list) {
            String approver = "user".equals(k.getApproverType())
                    ? Optional.ofNullable(names.get(k.getAssignedUserId())).orElse(tx.user)
                    : tx.rolePrefix + k.getRole();
            String by = k.getDecidedBy() == null ? "—" : names.getOrDefault(k.getDecidedBy(), "—");
            if (k.getDecidedAt() != null) by += "\n" + tx.dt.format(k.getDecidedAt().atZone(ZONE));
            t.addCell(cell(k.getStepName(), F_TD));
            t.addCell(cell(approver, F_TD));
            t.addCell(cell(tx.task(k.getStatus()), F_TD));
            t.addCell(cell(by, F_TD));
            t.addCell(cell(k.getComment() == null || k.getComment().isBlank() ? "—" : k.getComment(), F_TD));
        }
        doc.add(t);
    }

    // ---------------------------------------------------------------- assinaturas

    /** Celula do corpo para um campo de assinatura: imagem + "assinado por X em data". */
    private PdfPCell signatureCell(FormField f, Object value, int span,
                                   Map<Long, Signature> sigById, Tx tx, SigTx sx) {
        PdfPCell c = new PdfPCell();
        c.setColspan(span);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setPaddingTop(5);
        c.setPaddingBottom(6);
        c.setPaddingLeft(0);
        c.setPaddingRight(10);
        c.addElement(new Paragraph(f.label().toUpperCase(Locale.ROOT), F_LABEL));

        Long id = FormSchemaService.signatureId(value);
        Signature sig = id == null ? null : sigById.get(id);
        if (sig == null) {
            c.addElement(new Paragraph("—", F_EMPTY));
            return c;
        }
        Image img = loadImage(sig, 150, 70);
        if (img != null) {
            c.addElement(img);
        }
        String who = sig.getSignerName() == null ? sx.someone : sig.getSignerName();
        String when = sig.getSignedAt() == null ? "" : tx.dt.format(sig.getSignedAt().atZone(ZONE));
        Paragraph cap = new Paragraph(sx.signedBy + who + (when.isEmpty() ? "" : " · " + when), F_SUB);
        cap.setSpacingBefore(2);
        c.addElement(cap);
        c.addElement(new Paragraph(sx.styleName(sig.getStyle()), F_SUB));
        return c;
    }

    /** Folha(s) adicional(is): historico de assinaturas com a imagem e os metadados de cada uma. */
    private void signaturePages(Document doc, List<Signature> sigs, List<FormField> fields, Tx tx, SigTx sx)
            throws DocumentException {
        doc.newPage();
        Map<String, String> labels = new HashMap<>();
        for (FormField f : fields) if (FieldTypes.isSignature(f.type()) && f.key() != null) labels.put(f.key(), f.label());

        Paragraph h = new Paragraph(sx.historyTitle, F_TITLE);
        h.setSpacingAfter(2);
        doc.add(h);
        Paragraph lead = new Paragraph(sx.historyLead, F_SUB);
        lead.setSpacingAfter(10);
        doc.add(lead);

        int n = 0;
        for (Signature sig : sigs) {
            n++;
            Paragraph title = new Paragraph(sx.signature + " " + n
                    + "  ·  " + labels.getOrDefault(sig.getFieldKey(), sig.getFieldKey()), F_SECTION);
            title.setSpacingBefore(n == 1 ? 0 : 14);
            title.setSpacingAfter(4);
            doc.add(title);

            PdfPTable row = new PdfPTable(new float[]{2.2f, 5f});
            row.setWidthPercentage(100);
            // imagem: celula nao-composta (honra alinhamento vertical) com fundo branco
            Image img = loadImage(sig, 150, 150);
            PdfPCell imgCell = img != null ? new PdfPCell(img) : new PdfPCell(new Phrase(sx.imageUnavailable, F_EMPTY));
            imgCell.setBorderColor(LINE);
            imgCell.setPadding(8);
            imgCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            imgCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            imgCell.setBackgroundColor(new Color(0xff, 0xff, 0xff));
            row.addCell(imgCell);
            // metadados
            PdfPCell meta = new PdfPCell();
            meta.setBorderColor(LINE);
            meta.setPadding(0);
            meta.addElement(metaTable(sig, tx, sx));
            row.addCell(meta);
            doc.add(row);
        }
    }

    private PdfPTable metaTable(Signature sig, Tx tx, SigTx sx) {
        PdfPTable t = new PdfPTable(new float[]{2f, 4f});
        t.setWidthPercentage(100);
        metaRow(t, sx.mStyle, sx.styleName(sig.getStyle()));
        metaRow(t, sx.mSigner, sig.getSignerName() == null ? "—" : sig.getSignerName());
        metaRow(t, sx.mEmail, sig.getSignerEmail() == null ? "—" : sig.getSignerEmail());
        metaRow(t, sx.mWhen, sig.getSignedAt() == null ? "—" : tx.dt.format(sig.getSignedAt().atZone(ZONE)));
        metaRow(t, sx.mIp, sig.getIpAddress() == null ? "—" : sig.getIpAddress());
        metaRow(t, sx.mDevice, sig.getUserAgent() == null ? "—" : sig.getUserAgent());
        metaRow(t, sx.mLocation, geoText(sig, sx));
        return t;
    }

    private void metaRow(PdfPTable t, String label, String value) {
        PdfPCell k = new PdfPCell(new Phrase(label, F_LABEL));
        k.setBackgroundColor(BAND);
        k.setBorderColor(LINE);
        k.setPadding(4);
        k.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(k);
        PdfPCell v = new PdfPCell(new Phrase(value, F_TD));
        v.setBorderColor(LINE);
        v.setPadding(4);
        t.addCell(v);
    }

    private static String geoText(Signature sig, SigTx sx) {
        if (!"ok".equals(sig.getGeoStatus()) || sig.getGeoLat() == null || sig.getGeoLon() == null) {
            return sx.geoStatus(sig.getGeoStatus());
        }
        String coord = String.format(Locale.ROOT, "%.6f, %.6f", sig.getGeoLat(), sig.getGeoLon());
        if (sig.getGeoAccuracy() != null) {
            coord += " (±" + Math.round(sig.getGeoAccuracy()) + " m)";
        }
        return coord;
    }

    /** Carrega a imagem da assinatura do disco e ajusta ao retangulo (w x h), ou null se falhar. */
    private Image loadImage(Signature sig, float maxW, float maxH) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(storage.resolve(sig.getStoredPath()));
            Image img = Image.getInstance(bytes);
            img.scaleToFit(maxW, maxH);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- celulas

    private PdfPCell sectionCell(FormField f) {
        Phrase p = new Phrase(f.label(), F_SECTION);
        PdfPCell c = new PdfPCell(p);
        c.setColspan(12);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setPaddingTop(12);
        c.setPaddingBottom(4);
        c.setPaddingLeft(0);
        if (f.help() != null && !f.help().isBlank()) {
            p.add(new Chunk("\n" + f.help(), F_SUB));
        }
        return c;
    }

    private PdfPCell fieldCell(FormField f, Object value, int span, Tx tx) {
        String text = format(f, value, tx);
        boolean empty = text.isEmpty();
        Phrase p = new Phrase();
        p.add(new Chunk(f.label().toUpperCase(Locale.ROOT) + "\n", F_LABEL));
        p.add(new Chunk(empty ? "—" : text, empty ? F_EMPTY : F_VALUE));
        PdfPCell c = new PdfPCell(p);
        c.setColspan(span);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setPaddingTop(5);
        c.setPaddingBottom(6);
        c.setPaddingLeft(0);
        c.setPaddingRight(10);
        c.setLeading(0, 1.25f);
        return c;
    }

    private static PdfPCell cell(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "—" : text, f));
        c.setBorderColor(LINE);
        c.setPadding(4);
        return c;
    }

    /** Completa a linha da grade com celulas vazias sem borda. */
    private static void fill(PdfPTable grid, int used) {
        if (used == 0) return;
        PdfPCell c = new PdfPCell(new Phrase(""));
        c.setColspan(12 - used);
        c.setBorder(Rectangle.NO_BORDER);
        grid.addCell(c);
    }

    private static int spanOf(String width) {
        return "half".equals(width) ? 6 : "third".equals(width) ? 4 : 12;
    }

    // ---------------------------------------------------------------- valores

    private static String format(FormField f, Object value, Tx tx) {
        if (value == null) return "";
        String type = f.type();
        if ("checkbox".equals(type)) {
            boolean b = (value instanceof Boolean bb) ? bb
                    : List.of("true", "1", "on", "sim").contains(value.toString().toLowerCase(Locale.ROOT));
            return b ? tx.yes : tx.no;
        }
        if (FieldTypes.isAnyFile(type)) return attachmentNames(value);
        if (FieldTypes.isLookup(type)) return lookupText(value);
        if (value instanceof Collection<?> c) {
            StringJoiner j = new StringJoiner(", ");
            for (Object o : c) j.add(String.valueOf(o));
            return j.toString();
        }
        String s = value.toString();
        if ("date".equals(type)) {
            try { return tx.d.format(LocalDate.parse(s)); } catch (Exception ignored) { /* mantem o texto */ }
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static String lookupText(Object value) {
        List<?> items = value instanceof Collection<?> c ? new ArrayList<>(c) : List.of(value);
        StringJoiner j = new StringJoiner("; ");
        for (Object o : items) {
            if (o instanceof Map<?, ?> m && m.get("label") != null) j.add(m.get("label").toString());
        }
        return j.toString();
    }

    private static String attachmentNames(Object value) {
        List<?> items = value instanceof Collection<?> c ? new ArrayList<>(c) : List.of(value);
        StringJoiner j = new StringJoiner(", ");
        for (Object o : items) {
            if (o instanceof Map<?, ?> m && m.get("name") != null) j.add(m.get("name").toString());
            else if (o != null) j.add(o.toString());
        }
        return j.toString();
    }

    private Map<String, Object> readData(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", false, size, style, color);
    }

    /** Rodape com paginacao e data de emissao. */
    private static final class Footer extends PdfPageEventHelper {
        private final String left;
        private final String stamp;
        private final Tx tx;

        Footer(String form, long id, Tx tx) {
            this.left = "Harmonis · " + form + " #" + id;
            this.tx = tx;
            this.stamp = tx.dt.format(Instant.now().atZone(ZONE));
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font f = font(8, Font.NORMAL, MUTED);
            float y = document.bottom() - 22;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(left, f), document.left(), y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase(tx.issuedOn + stamp + " · " + tx.page + writer.getPageNumber(), f),
                    document.right(), y, 0);
        }
    }
}
