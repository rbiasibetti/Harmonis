package app.harmonis.portal;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

/**
 * Renderiza Markdown para HTML e SANITIZA o resultado (anti-XSS), removendo
 * qualquer HTML perigoso (scripts, handlers on*, iframes, etc.).
 */
@Service
public class MarkdownService {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    private final Safelist safelist = Safelist.relaxed()
            .addAttributes("a", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https");

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String rawHtml = renderer.render(parser.parse(markdown));
        return Jsoup.clean(rawHtml, safelist);
    }
}
