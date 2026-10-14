package app.harmonis.forms;

import app.harmonis.forms.FormDtos.*;
import app.harmonis.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forms")
public class FormController {

    private final FormService service;
    private final FormAttachmentService attachments;

    public FormController(FormService service, FormAttachmentService attachments) {
        this.service = service;
        this.attachments = attachments;
    }

    @GetMapping("/field-types")
    @PreAuthorize("hasAuthority('forms.view')")
    public List<FieldTypes.TypeInfo> fieldTypes() {
        return FieldTypes.ALL;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('forms.view')")
    public List<FormSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('forms.view')")
    public FormDetail get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('forms.create')")
    public FormDetail create(@Valid @RequestBody SaveFormRequest req,
                             @AuthenticationPrincipal AuthPrincipal principal) {
        return service.create(req, principal.userId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('forms.update')")
    public FormDetail update(@PathVariable long id, @Valid @RequestBody SaveFormRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('forms.delete')")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('forms.view')")
    public java.util.Map<String, Object> submit(@PathVariable long id,
                                                @Valid @RequestBody SubmitRequest req,
                                                @AuthenticationPrincipal AuthPrincipal principal) {
        long submissionId = service.submit(id, req.data(), principal.userId());
        return java.util.Map.of("id", submissionId);
    }

    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasAuthority('forms.submissions')")
    public List<SubmissionView> submissions(@PathVariable long id) {
        return service.submissions(id);
    }

    // ----- anexos -----

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('forms.view')")
    public AttachmentRef uploadAttachment(@PathVariable long id,
                                          @RequestParam("fieldKey") String fieldKey,
                                          @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                          @AuthenticationPrincipal AuthPrincipal principal) {
        return attachments.upload(id, fieldKey, file, principal.userId());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('forms.view')")
    public void deleteAttachment(@PathVariable long attachmentId,
                                 @AuthenticationPrincipal AuthPrincipal principal) {
        attachments.deleteUncommitted(attachmentId, principal.userId());
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("hasAuthority('forms.view')")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.FileSystemResource> downloadAttachment(
            @PathVariable long attachmentId) {
        FormAttachmentService.Downloadable d = attachments.download(attachmentId);
        org.springframework.core.io.FileSystemResource resource =
                new org.springframework.core.io.FileSystemResource(d.path());
        if (!resource.exists()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado.");
        }
        String filename = java.net.URLEncoder.encode(d.originalName(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        org.springframework.http.MediaType mediaType = d.mimeType() != null
                ? org.springframework.http.MediaType.parseMediaType(d.mimeType())
                : org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping("/{id}/flow")
    @PreAuthorize("hasAuthority('forms.update')")
    public FlowView getFlow(@PathVariable long id) {
        return service.getFlow(id);
    }

    @PutMapping("/{id}/flow")
    @PreAuthorize("hasAuthority('forms.update')")
    public FlowView saveFlow(@PathVariable long id, @Valid @RequestBody SaveFlowRequest req) {
        return service.saveFlow(id, req.nodes());
    }

    @GetMapping("/{id}/submissions.csv")
    @PreAuthorize("hasAuthority('forms.submissions')")
    public org.springframework.http.ResponseEntity<byte[]> csv(@PathVariable long id) {
        byte[] body = service.exportCsv(id);
        String filename = "respostas-" + id + ".csv";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(body);
    }
}
