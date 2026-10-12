package app.harmonis.portal;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class PageDtos {

    private PageDtos() {}

    public record PageSummary(
            Long id, String title, String slug, String excerpt,
            String status, Instant publishedAt, Instant updatedAt
    ) {}

    public record PortalItem(String title, String slug, String excerpt, Instant publishedAt) {}

    public record RenderedPage(String title, String slug, String html, Instant publishedAt) {}

    public record PageEdit(
            Long id, String title, String slug, String excerpt, String content,
            String status, Instant publishedAt
    ) {}

    public record SavePageRequest(
            @NotBlank String title, String excerpt, String content, String status
    ) {}

    public record PreviewRequest(String content) {}

    public record PreviewResponse(String html) {}
}
