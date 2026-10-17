package app.harmonis.identity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LanguagesTest {

    @Test
    void normalizaCodigosParaIdiomasSuportados() {
        assertEquals("pt-BR", Languages.normalize("pt"));
        assertEquals("pt-BR", Languages.normalize("PT-br"));
        assertEquals("en", Languages.normalize("en-US"));
        assertEquals("es", Languages.normalize("ES"));
        assertEquals("pt-BR", Languages.normalize("fr"));
        assertEquals("pt-BR", Languages.normalize(null));
    }

    @Test
    void requireSupportedAceitaSoCodigosExatos() {
        assertEquals("en", Languages.requireSupported("en"));
        assertEquals("pt-BR", Languages.requireSupported(" pt-BR "));
        assertNull(Languages.requireSupported("fr"));
        assertNull(Languages.requireSupported("EN"));
        assertNull(Languages.requireSupported(null));
    }

    @Test
    void joinOrdenaSemRepetirERejeitaInvalidosOuVazio() {
        assertEquals("pt-BR,en", Languages.join(List.of("en", "pt-BR")));
        assertEquals("es", Languages.join(List.of("es")));
        assertNull(Languages.join(List.of()));
        assertNull(Languages.join(null));
        assertNull(Languages.join(List.of("en", "fr")));
    }

    @Test
    void parseDevolveTodosQuandoVazioEIgnoraDesconhecidos() {
        assertEquals(List.of("pt-BR", "en", "es"), Languages.parse(null));
        assertEquals(List.of("en", "es"), Languages.parse("en, es ,xx"));
    }
}
