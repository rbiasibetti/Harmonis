package app.harmonis.documents;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class DocumentDtos {

    private DocumentDtos() {}

    public record FolderRef(Long id, String name) {}

    public record FolderView(Long id, String name, Instant createdAt) {}

    public record DocumentView(
            Long id, String name, String description, int currentVersion,
            Map<String, String> metadata, Instant createdAt, Instant updatedAt
    ) {}

    public record VersionView(
            Long id, int version, String originalName, String mimeType, long size,
            String uploadedBy, Instant createdAt
    ) {}

    public record DocumentDetail(
            Long id, Long folderId, String name, String description, int currentVersion,
            Map<String, String> metadata, List<VersionView> versions
    ) {}

    public record BrowseView(
            FolderRef current, List<FolderRef> breadcrumb,
            List<FolderView> folders, List<DocumentView> documents
    ) {}

    public record SearchHit(DocumentView document, Long folderId, String folderName) {}

    public record CreateFolderRequest(@NotBlank String name, Long parentId) {}

    public record RenameRequest(@NotBlank String name) {}

    public record UpdateDocumentRequest(@NotBlank String name, String description, Map<String, String> metadata) {}
}
