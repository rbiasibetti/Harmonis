package app.harmonis.roles;

import app.harmonis.roles.RoleDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('roles.view')")
    public List<RoleView> list() {
        return service.list();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('roles.view')")
    public PermissionCatalog catalog() {
        return service.catalog();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('roles.create')")
    public RoleView create(@Valid @RequestBody SaveRoleRequest req) {
        return service.create(req);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('roles.update')")
    public RoleView update(@PathVariable long id, @Valid @RequestBody SaveRoleRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('roles.delete')")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
