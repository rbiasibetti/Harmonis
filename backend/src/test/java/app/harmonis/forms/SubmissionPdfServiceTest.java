package app.harmonis.forms;

import app.harmonis.identity.UserRepository;
import app.harmonis.workflow.ProcessInstanceRepository;
import app.harmonis.workflow.ProcessTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionPdfServiceTest {

    private static final String SCHEMA = """
            {"fields":[
             {"id":"a","key":"nome","type":"text","label":"Nome completo","width":"half"},
             {"id":"b","key":"data","type":"date","label":"Data de nascimento","width":"half"},
             {"id":"s","type":"section","label":"Dados da compra"},
             {"id":"c","key":"ativo","type":"checkbox","label":"Urgente","width":"third"},
             {"id":"d","key":"itens","type":"multiselect","label":"Itens","width":"third","options":["A","B"]},
             {"id":"e","key":"obs","type":"textarea","label":"Observação","width":"third"},
             {"id":"f","key":"extra","type":"text","label":"Campo oculto",
              "visibleWhen":{"field":"ativo","operator":"equals","value":"true"}}
            ]}""";

    private FormRepository forms;
    private FormSubmissionRepository submissions;
    private SubmissionPdfService service;
    private FormSubmission sub;

    @BeforeEach
    void setUp() {
        forms = mock(FormRepository.class);
        submissions = mock(FormSubmissionRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        UserRepository users = mock(UserRepository.class);
        when(users.findAll()).thenReturn(List.of());
        service = new SubmissionPdfService(forms, submissions, mock(ProcessInstanceRepository.class),
                mock(ProcessTaskRepository.class), users, new FormSchemaService(mapper), mapper);

        Form form = mock(Form.class);
        when(form.getId()).thenReturn(7L);
        when(form.getName()).thenReturn("Solicitação de compra");
        when(form.getSlug()).thenReturn("compra");
        when(form.getSchemaJson()).thenReturn(SCHEMA);
        when(forms.findById(7L)).thenReturn(Optional.of(form));

        sub = mock(FormSubmission.class);
        when(sub.getId()).thenReturn(42L);
        when(sub.getFormId()).thenReturn(7L);
        when(sub.getCreatedAt()).thenReturn(Instant.parse("2026-03-10T12:00:00Z"));
        when(sub.getDataJson()).thenReturn(
                "{\"nome\":\"Maria José\",\"data\":\"1990-05-17\",\"ativo\":false,\"itens\":[\"A\",\"B\"],\"extra\":\"nao deve aparecer\"}");
        when(submissions.findById(42L)).thenReturn(Optional.of(sub));
    }

    private String text(SubmissionPdfService.Pdf pdf) throws Exception {
        PdfReader r = new PdfReader(pdf.bytes());
        PdfTextExtractor ex = new PdfTextExtractor(r);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= r.getNumberOfPages(); i++) sb.append(ex.getTextFromPage(i)).append('\n');
        return sb.toString();
    }

    @Test
    void geraPdfComRotulosEValoresFormatados() throws Exception {
        SubmissionPdfService.Pdf pdf = service.render(7L, 42L);
        assertEquals("registro-compra-42.pdf", pdf.filename());
        assertTrue(new String(pdf.bytes(), 0, 5).startsWith("%PDF"));

        String t = text(pdf);
        assertTrue(t.contains("Solicitação de compra"), t);
        assertTrue(t.contains("Registro nº 42"), t);
        assertTrue(t.contains("NOME COMPLETO"), t);
        assertTrue(t.contains("Maria José"), t);
        assertTrue(t.contains("17/05/1990"), t);          // data formatada
        assertTrue(t.contains("Dados da compra"), t);       // secao
        assertTrue(t.contains("Não"), t);                   // checkbox
        assertTrue(t.contains("A, B"), t);                  // multiselecao
        assertFalse(t.contains("nao deve aparecer"), t);    // campo condicional oculto
    }

    @Test
    void registroDeOutroFormularioNaoEncontrado() {
        assertThrows(ResponseStatusException.class, () -> service.render(99L, 42L));
        assertThrows(ResponseStatusException.class, () -> service.render(7L, 1000L));
    }
}
