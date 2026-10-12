package app.harmonis.portal;

import app.harmonis.portal.PageDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;

@Service
public class PageService {

    private final PageRepository pages;
    private final MarkdownService markdown;

    public PageService(PageRepository pages, MarkdownService markdown) {
        this.pages = pages;
        this.markdown = markdown;
    }

    // ----- gestao (exige permissao de edicao) -----

    @Transactional(readOnly = true)
    public List<PageSummary> listManagement() {
        return pages.findAllByOrderByUpdatedAtDesc().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public PageEdit getForEdit(long id) {
        Page p = require(id);
        return new PageEdit(p.getId(), p.getTitle(), p.getSlug(), p.getExcerpt(),
                p.getContent(), p.getStatus(), p.getPublishedAt());
    }

    @Transactional
    public PageEdit create(SavePageRequest req, Long userId) {
        Page p = new Page();
        p.setTitle(req.title().trim());
        p.setSlug(uniqueSlug(slugify(req.title())));
        p.setExcerpt(blankToNull(req.excerpt()));
        p.setContent(req.content());
        p.setStatus(normalizeStatus(req.status()));
        p.setCreatedBy(userId);
        if (p.isPublished()) p.setPublishedAt(Instant.now());
        pages.save(p);
        return getForEdit(p.getId());
    }

    @Transactional
    public PageEdit update(long id, SavePageRequest req) {
        Page p = require(id);
        p.setTitle(req.title().trim());
        p.setExcerpt(blankToNull(req.excerpt()));
        p.setContent(req.content());
        String status = normalizeStatus(req.status());
        // define published_at na primeira publicacao
        if (status.equals("published") && p.getPublishedAt() == null) {
            p.setPublishedAt(Instant.now());
        }
        p.setStatus(status);
        pages.save(p);
        return getForEdit(id);
    }

    @Transactional
    public void delete(long id) {
        pages.delete(require(id));
    }

    public PreviewResponse preview(String content) {
        return new PreviewResponse(markdown.render(content));
    }

    // ----- portal (publicadas) -----

    @Transactional(readOnly = true)
    public List<PortalItem> listPortal() {
        return pages.findByStatusOrderByPublishedAtDesc("published").stream()
                .map(p -> new PortalItem(p.getTitle(), p.getSlug(), p.getExcerpt(), p.getPublishedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RenderedPage getPublished(String slug) {
        Page p = pages.findBySlug(slug)
                .filter(Page::isPublished)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Página não encontrada."));
        return new RenderedPage(p.getTitle(), p.getSlug(), markdown.render(p.getContent()), p.getPublishedAt());
    }

    // ----- helpers -----

    private Page require(long id) {
        return pages.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Página não encontrada."));
    }

    private PageSummary toSummary(Page p) {
        return new PageSummary(p.getId(), p.getTitle(), p.getSlug(), p.getExcerpt(),
                p.getStatus(), p.getPublishedAt(), p.getUpdatedAt());
    }

    private String normalizeStatus(String status) {
        return "published".equals(status) ? "published" : "draft";
    }

    private String uniqueSlug(String base) {
        String slug = base.isBlank() ? "pagina" : base;
        String candidate = slug;
        int n = 2;
        while (pages.existsBySlug(candidate)) candidate = slug + "-" + n++;
        return candidate;
    }

    private static String slugify(String raw) {
        String noAccents = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
