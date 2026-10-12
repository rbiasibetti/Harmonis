package app.harmonis.portal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownServiceTest {

    private final MarkdownService md = new MarkdownService();

    @Test
    void renderiza_markdown_basico() {
        String html = md.render("# Título\n\nUm **negrito** e um [link](https://exemplo.com).");
        assertTrue(html.contains("<h1>Título</h1>"));
        assertTrue(html.contains("<strong>negrito</strong>"));
        assertTrue(html.contains("href=\"https://exemplo.com\""));
    }

    @Test
    void remove_script_embutido() {
        String html = md.render("Olá <script>alert('xss')</script> mundo");
        assertFalse(html.toLowerCase().contains("<script"));
        assertFalse(html.toLowerCase().contains("alert"));
        assertTrue(html.contains("Olá"));
    }

    @Test
    void remove_handlers_e_javascript_href() {
        String html = md.render("<a href=\"javascript:alert(1)\" onclick=\"steal()\">clique</a>");
        assertFalse(html.toLowerCase().contains("javascript:"));
        assertFalse(html.toLowerCase().contains("onclick"));
    }

    @Test
    void remove_iframe() {
        String html = md.render("texto\n\n<iframe src=\"http://mal.example\"></iframe>");
        assertFalse(html.toLowerCase().contains("<iframe"));
    }

    @Test
    void conteudo_vazio_retorna_string_vazia() {
        assertEquals("", md.render(null));
        assertEquals("", md.render("   "));
    }
}
