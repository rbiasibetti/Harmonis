package app.harmonis.documents;

import app.harmonis.documents.DocumentDtos.*;
import app.harmonis.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('documents.view')")
    public BrowseView browse(@RequestParam(name = "folder", required = false) Long folderId) {
        return service.browse(folderId);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('documents.view')")
    public List<SearchHit> search(@RequestParam("q") String q) {
        return service.search(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('documents.view')")
    public DocumentDetail detail(@PathVariable long id) {
        return service.detail(id);
    }

    // ----- pastas -----

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('documents.create')")
    public FolderView createFolder(@Valid @RequestBody CreateFolderRequest req,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        return service.createFolder(req.name(), req.parentId(), principal.userId());
    }

    @PutMapping("/folders/{id}")
    @PreAuthorize("hasAuthority('documents.update')")
    public FolderView renameFolder(@PathVariable long id, @Valid @RequestBody RenameRequest req) {
        return service.renameFolder(id, req.name());
    }

    @DeleteMapping("/folders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('documents.delete')")
    public void deleteFolder(@PathVariable long id) {
        service.deleteFolder(id);
    }

    // ----- documentos -----

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('documents.create')")
    public DocumentDetail upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "folderId", required = false) Long folderId,
                                 @RequestParam(value = "name", required = false) String name,
                                 @RequestParam(value = "description", required = false) String description,
                                 @AuthenticationPrincipal AuthPrincipal principal) {
        return service.upload(folderId, file, name, description, principal.userId());
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('documents.create')")
    public DocumentDetail addVersion(@PathVariable long id, @RequestParam("file") MultipartFile file,
                                     @AuthenticationPrincipal AuthPrincipal principal) {
        return service.addVersion(id, file, principal.userId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('documents.update')")
    public DocumentDetail update(@PathVariable long id, @Valid @RequestBody UpdateDocumentRequest req) {
        return service.updateDocument(id, req.name(), req.description(), req.metadata());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('documents.delete')")
    public void delete(@PathVariable long id) {
        service.deleteDocument(id);
    }

    // ----- download (sempre autenticado) -----

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('documents.view')")
    public ResponseEntity<FileSystemResource> download(@PathVariable long id) {
        return fileResponse(service.download(id));
    }

    @GetMapping("/versions/{versionId}/download")
    @PreAuthorize("hasAuthority('documents.view')")
    public ResponseEntity<FileSystemResource> downloadVersion(@PathVariable long versionId) {
        return fileResponse(service.downloadVersion(versionId));
    }

    private ResponseEntity<FileSystemResource> fileResponse(DocumentService.Downloadable d) {
        FileSystemResource resource = new FileSystemResource(d.path());
        if (!resource.exists()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado.");
        }
        String filename = URLEncoder.encode(d.originalName(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = d.mimeType() != null
                ? MediaType.parseMediaType(d.mimeType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(mediaType)
                .body(resource);
    }
}
