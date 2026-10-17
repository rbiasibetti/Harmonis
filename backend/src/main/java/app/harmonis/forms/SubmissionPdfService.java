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

    private static final Color INK = new Color(0x1f, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6b, 0x72, 0x80);
    private static final Color LINE = new Color(0xd1, 0xd5, 0xdb);
    private static final Color BAND = new Color(0xf3, 0xf4, 0xf6);
    private static final Color ACCENT = new Color(0x1d, 0x4e, 0xd8);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
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

    public SubmissionPdfService(FormRepository forms, FormSubmissionRepository submissions,
                                ProcessInstanceRepository instances, ProcessTaskRepository tasks,
                                UserRepository users, FormSchemaService schema, ObjectMapper mapper) {
        this.forms = forms;
        this.submissions = submissions;
        this.instances = instances;
        this.tasks = tasks;
        this.users = users;
        this.schema = schema;
        this.mapper = mapper;
    }

    /** PDF do registro; formId (opcional) garante que o registro pertence ao formulario da URL. */
    @Transactional(readOnly = true)
    public Pdf render(Long formId, long submissionId) {
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

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 40, 44);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer(form.getName(), sub.getId()));
            doc.addTitle(form.getName() + " #" + sub.getId());
            doc.addCreator("Harmonis");
            doc.open();

            header(doc, form, sub, inst, names);
            body(doc, fields, data);
            if (inst != null) history(doc, inst, taskList, names);

            doc.close();
            return new Pdf(out.toByteArray(), "registro-" + (form.getSlug() == null ? form.getId() : form.getSlug())
                    + "-" + sub.getId() + ".pdf");
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar o PDF.", e);
        }
    }

    @Transactional(readOnly = true)
    public Pdf renderForProcess(long instanceId) {
        ProcessInstance inst = instances.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo não encontrado."));
        return render(null, inst.getSubmissionId());
    }

    // ---------------------------------------------------------------- secoes

    private void header(Document doc, Form form, FormSubmission sub, ProcessInstance inst,
                        Map<Long, String> names) throws DocumentException {
        Paragraph title = new Paragraph(form.getName(), F_TITLE);
        title.setSpacingAfter(2);
        doc.add(title);

        List<String> meta = new ArrayList<>();
        meta.add("Registro nº " + sub.getId());
        if (sub.getCreatedAt() != null) meta.add("Enviado em " + DT.format(sub.getCreatedAt().atZone(ZONE)));
        if (sub.getSubmittedBy() != null && names.get(sub.getSubmittedBy()) != null) {
            meta.add("por " + names.get(sub.getSubmittedBy()));
        }
        if (inst != null) meta.add("Situação: " + statusLabel(inst.getStatus()));
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
    private void body(Document doc, List<FormField> fields, Map<String, Object> data) throws DocumentException {
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
            grid.addCell(fieldCell(f, data.get(f.key()), span));
            used = (used + span) % 12;
            any = true;
        }
        fill(grid, used);

        if (!any) {
            doc.add(new Paragraph("Este formulário não possui campos.", F_EMPTY));
            return;
        }
        doc.add(grid);
    }

    private void history(Document doc, ProcessInstance inst, List<ProcessTask> list,
                         Map<Long, String> names) throws DocumentException {
        Paragraph h = new Paragraph("Histórico de aprovação", F_SECTION);
        h.setSpacingBefore(14);
        h.setSpacingAfter(4);
        doc.add(h);

        if (list.isEmpty()) {
            doc.add(new Paragraph("Nenhuma etapa de aprovação foi necessária.", F_EMPTY));
            return;
        }
        PdfPTable t = new PdfPTable(new float[]{3f, 3f, 2f, 3f, 4f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String col : new String[]{"ETAPA", "APROVADOR", "RESULTADO", "DECIDIDO POR / EM", "COMENTÁRIO"}) {
            PdfPCell c = new PdfPCell(new Phrase(col, F_TH));
            c.setBackgroundColor(BAND);
            c.setBorderColor(LINE);
            c.setPadding(4);
            t.addCell(c);
        }
        for (ProcessTask k : list) {
            String approver = "user".equals(k.getApproverType())
                    ? Optional.ofNullable(names.get(k.getAssignedUserId())).orElse("usuário")
                    : "Papel: " + k.getRole();
            String by = k.getDecidedBy() == null ? "—" : names.getOrDefault(k.getDecidedBy(), "—");
            if (k.getDecidedAt() != null) by += "\n" + DT.format(k.getDecidedAt().atZone(ZONE));
            t.addCell(cell(k.getStepName(), F_TD));
            t.addCell(cell(approver, F_TD));
            t.addCell(cell(taskLabel(k.getStatus()), F_TD));
            t.addCell(cell(by, F_TD));
            t.addCell(cell(k.getComment() == null || k.getComment().isBlank() ? "—" : k.getComment(), F_TD));
        }
        doc.add(t);
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

    private PdfPCell fieldCell(FormField f, Object value, int span) {
        String text = format(f, value);
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

    private static String format(FormField f, Object value) {
        if (value == null) return "";
        String type = f.type();
        if ("checkbox".equals(type)) {
            boolean b = (value instanceof Boolean bb) ? bb
                    : List.of("true", "1", "on", "sim").contains(value.toString().toLowerCase(Locale.ROOT));
            return b ? "Sim" : "Não";
        }
        if (FieldTypes.isAnyFile(type)) return attachmentNames(value);
        if (value instanceof Collection<?> c) {
            StringJoiner j = new StringJoiner(", ");
            for (Object o : c) j.add(String.valueOf(o));
            return j.toString();
        }
        String s = value.toString();
        if ("date".equals(type)) {
            try { return D.format(LocalDate.parse(s)); } catch (Exception ignored) { /* mantem o texto */ }
        }
        return s;
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

    private static String statusLabel(String s) {
        return switch (s == null ? "" : s) {
            case "approved" -> "Aprovado";
            case "rejected" -> "Reprovado";
            case "pending" -> "Em andamento";
            default -> s;
        };
    }

    private static String taskLabel(String s) {
        return switch (s == null ? "" : s) {
            case "approved" -> "Aprovado";
            case "rejected" -> "Reprovado";
            case "pending" -> "Pendente";
            case "skipped" -> "Dispensado";
            default -> s;
        };
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
        private final String stamp = DT.format(Instant.now().atZone(ZONE));

        Footer(String form, long id) { this.left = "Harmonis · " + form + " #" + id; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font f = font(8, Font.NORMAL, MUTED);
            float y = document.bottom() - 22;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(left, f), document.left(), y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Emitido em " + stamp + " · Página " + writer.getPageNumber(), f),
                    document.right(), y, 0);
        }
    }
}
