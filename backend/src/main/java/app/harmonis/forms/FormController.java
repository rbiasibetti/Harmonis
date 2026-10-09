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

    public FormController(FormService service) {
        this.service = service;
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
