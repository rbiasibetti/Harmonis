package app.harmonis.roles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class RoleDtos {

    private RoleDtos() {}

    public record RoleView(
            Long id,
            String name,
            String label,
            boolean system,
            List<String> permissions
    ) {}

    public record PermissionGroup(String group, List<String> permissions) {}

    /** Catalogo de permissoes para o editor de papeis. */
    public record PermissionCatalog(List<PermissionGroup> groups) {}

    public record SaveRoleRequest(
            @NotBlank String name,
            String label,
            @NotNull List<String> permissions
    ) {}
}
