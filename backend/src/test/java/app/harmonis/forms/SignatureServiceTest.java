package app.harmonis.forms;

import app.harmonis.documents.DocumentStorage;
import app.harmonis.forms.FormDtos.SignatureRef;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SignatureServiceTest {

    private static final String SCHEMA = """
            {"fields":[
             {"id":"a","key":"assina","type":"signature","label":"Assinatura","display":"both"},
             {"id":"b","key":"so_desenho","type":"signature","label":"Visto","display":"drawn"},
             {"id":"c","key":"obs","type":"text","label":"Observação"}
            ]}""";

    private final Map<Long, Signature> db = new HashMap<>();
    private long seq = 0;
    private FormRepository forms;
    private SignatureRepository repo;
    private UserRepository users;
    private SignatureService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        forms = mock(FormRepository.class);
        repo = mock(SignatureRepository.class);
        users = mock(UserRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        when(storage.store(any())).thenReturn(new DocumentStorage.Stored("acme/x.png", "x.png", "image/png", 1234));
        when(repo.save(any(Signature.class))).thenAnswer(i -> {
            Signature s = i.getArgument(0);
            if (s.getId() == null) {
                try { var f = Signature.class.getDeclaredField("id"); f.setAccessible(true); f.set(s, ++seq); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
            db.put(s.getId(), s);
            return s;
        });
        when(repo.findById(any())).thenAnswer(i -> Optional.ofNullable(db.get(i.getArgument(0))));

        Form form = mock(Form.class);
        when(form.getId()).thenReturn(7L);
        when(form.getSchemaJson()).thenReturn(SCHEMA);
        when(forms.findById(7L)).thenReturn(Optional.of(form));

        User u = mock(User.class);
        when(u.getName()).thenReturn("Maria");
        when(u.getEmail()).thenReturn("maria@acme.test");
        when(users.findById(5L)).thenReturn(Optional.of(u));

        service = new SignatureService(forms, repo, new FormSchemaService(mapper), storage, users);
    }

    private MultipartFile png() {
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(false);
        when(f.getSize()).thenReturn(1234L);
        when(f.getContentType()).thenReturn("image/png");
        return f;
    }

    private HttpServletRequest http() {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (Linux)");
        when(r.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.1");
        when(r.getRemoteAddr()).thenReturn("10.0.0.1");
        return r;
    }

    @Test
    void capturaGravaMetadadosConfiaveisDoServidor() {
        var geo = new SignatureService.ClientGeo("ok", -23.5, -46.6, 12.0);
        SignatureRef ref = service.capture(7L, "assina", "drawn", png(), geo, http(), 5L);

        assertNotNull(ref.signatureId());
        assertEquals("drawn", ref.style());
        assertEquals("Maria", ref.signer().name());
        assertEquals("maria@acme.test", ref.signer().email());
        assertEquals("203.0.113.9", ref.ip(), "usa o primeiro IP do X-Forwarded-For");
        assertEquals("Mozilla/5.0 (Linux)", ref.device());
        assertEquals("ok", ref.geo().status());
        assertEquals(-23.5, ref.geo().lat());
    }

    @Test
    void selfieNaoCabeEmCampoSoDesenho() {
        assertThrows(ResponseStatusException.class,
                () -> service.capture(7L, "so_desenho", "selfie", png(), null, http(), 5L));
    }

    @Test
    void estiloInvalidoOuCampoNaoAssinaturaSaoRejeitados() {
        assertThrows(ResponseStatusException.class,
                () -> service.capture(7L, "assina", "xpto", png(), null, http(), 5L));
        assertThrows(ResponseStatusException.class,
                () -> service.capture(7L, "obs", "drawn", png(), null, http(), 5L));
    }

    @Test
    void geoBestEffortRegistraStatusSemCoordenadasQuandoNegada() {
        SignatureRef ref = service.capture(7L, "assina", "drawn", png(),
                new SignatureService.ClientGeo("denied", null, null, null), http(), 5L);
        assertEquals("denied", ref.geo().status());
        assertNull(ref.geo().lat());
    }

    @Test
    void geoOkSemCoordenadasViraIndisponivel() {
        SignatureRef ref = service.capture(7L, "assina", "drawn", png(),
                new SignatureService.ClientGeo("ok", null, null, null), http(), 5L);
        assertEquals("unavailable", ref.geo().status());
    }

    @Test
    void commitVinculaARespostaEReescreveOValorComMetadados() {
        SignatureRef ref = service.capture(7L, "assina", "drawn", png(),
                new SignatureService.ClientGeo("ok", 1.0, 2.0, 3.0), http(), 5L);
        Form form = forms.findById(7L).orElseThrow();
        FormField field = new FormSchemaService(new ObjectMapper()).readFields(SCHEMA).stream()
                .filter(f -> "assina".equals(f.key())).findFirst().orElseThrow();

        Map<String, Object> data = new HashMap<>();
        data.put("assina", Map.of("signatureId", ref.signatureId()));
        service.commit(form, 99L, data, List.of(field), 5L);

        assertEquals(99L, db.get(ref.signatureId()).getSubmissionId());
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) data.get("assina");
        assertEquals(ref.signatureId(), value.get("signatureId"));
        assertEquals("drawn", value.get("style"));
        assertEquals("Mozilla/5.0 (Linux)", value.get("device"));
        assertNotNull(value.get("signedAt"));
    }

    @Test
    void commitRejeitaAssinaturaDeOutroFormulario() {
        SignatureRef ref = service.capture(7L, "assina", "drawn", png(), null, http(), 5L);
        db.get(ref.signatureId()).setFormId(999L); // simula outra origem
        Form form = forms.findById(7L).orElseThrow();
        FormField field = new FormSchemaService(new ObjectMapper()).readFields(SCHEMA).stream()
                .filter(f -> "assina".equals(f.key())).findFirst().orElseThrow();
        Map<String, Object> data = new HashMap<>();
        data.put("assina", Map.of("signatureId", ref.signatureId()));
        assertThrows(ResponseStatusException.class, () -> service.commit(form, 99L, data, List.of(field), 5L));
    }
}
