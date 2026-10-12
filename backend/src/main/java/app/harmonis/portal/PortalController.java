package app.harmonis.portal;

import app.harmonis.portal.PageDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Portal de leitura: apenas paginas publicadas. */
@RestController
@RequestMapping("/api/portal")
@PreAuthorize("hasAuthority('pages.view')")
public class PortalController {

    private final PageService service;

    public PortalController(PageService service) {
        this.service = service;
    }

    @GetMapping
    public List<PortalItem> list() {
        return service.listPortal();
    }

    @GetMapping("/{slug}")
    public RenderedPage show(@PathVariable String slug) {
        return service.getPublished(slug);
    }
}
