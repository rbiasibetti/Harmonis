package app.harmonis.portal;

import app.harmonis.portal.PageDtos.*;
import app.harmonis.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestao de paginas (CMS). Drafts visiveis apenas a quem pode editar. */
@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageService service;

    public PageController(PageService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pages.update')")
    public List<PageSummary> list() {
        return service.listManagement();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pages.update')")
    public PageEdit get(@PathVariable long id) {
        return service.getForEdit(id);
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('pages.update')")
    public PreviewResponse preview(@RequestBody PreviewRequest req) {
        return service.preview(req.content());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pages.create')")
    public PageEdit create(@Valid @RequestBody SavePageRequest req,
                           @AuthenticationPrincipal AuthPrincipal principal) {
        return service.create(req, principal.userId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pages.update')")
    public PageEdit update(@PathVariable long id, @Valid @RequestBody SavePageRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pages.delete')")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
