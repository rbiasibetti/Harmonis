package app.harmonis.roles;

import app.harmonis.identity.AclCatalog;
import app.harmonis.identity.Permission;
import app.harmonis.identity.PermissionRepository;
import app.harmonis.identity.Role;
import app.harmonis.identity.RoleRepository;
import app.harmonis.roles.RoleDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;

    public RoleService(RoleRepository roles, PermissionRepository permissions) {
        this.roles = roles;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        return roles.findAll().stream()
                .sorted((a, b) -> {
                    if (a.isSystem() != b.isSystem()) return a.isSystem() ? -1 : 1; // sistema primeiro
                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .map(RoleService::toView)
                .toList();
    }

    public PermissionCatalog catalog() {
        List<PermissionGroup> groups = AclCatalog.GROUPS.entrySet().stream()
                .map(e -> new PermissionGroup(e.getKey(), e.getValue()))
                .sorted((a, b) -> a.group().compareToIgnoreCase(b.group()))
                .toList();
        return new PermissionCatalog(groups);
    }

    @Transactional
    public RoleView create(SaveRoleRequest req) {
        String name = normalizeName(req.name());
        if (AclCatalog.SYSTEM_ROLES.contains(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nome reservado para papel de sistema.");
        }
        if (roles.findByName(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um papel com este nome.");
        }
        Role role = new Role(name, blankOr(req.label(), name), false);
        role.setPermissions(resolvePermissions(req.permissions()));
        roles.save(role);
        return toView(role);
    }

    @Transactional
    public RoleView update(long id, SaveRoleRequest req) {
        Role role = require(id);
        if (role.isSystem()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Papéis de sistema não podem ser editados.");
        }
        // Nome imutavel apos criacao (evita invalidar tokens/atribuicoes); so label + permissoes mudam.
        role.setLabel(blankOr(req.label(), role.getName()));
        role.setPermissions(resolvePermissions(req.permissions()));
        roles.save(role);
        return toView(role);
    }

    @Transactional
    public void delete(long id) {
        Role role = require(id);
        if (role.isSystem()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Papéis de sistema não podem ser removidos.");
        }
        roles.delete(role); // FK ON DELETE CASCADE remove vinculos em hm_user_roles
    }

    private Role require(long id) {
        return roles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Papel não encontrado."));
    }

    private Set<Permission> resolvePermissions(List<String> names) {
        Set<Permission> resolved = new HashSet<>();
        for (String n : names) {
            Permission p = permissions.findByName(n)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permissão inválida: " + n));
            resolved.add(p);
        }
        return resolved;
    }

    private static String normalizeName(String raw) {
        String noAccents = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().replaceAll("\\s+", "_").replaceAll("[^a-z0-9_]", "");
    }

    private static String blankOr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static RoleView toView(Role r) {
        List<String> perms = r.getPermissions().stream().map(Permission::getName).sorted().toList();
        return new RoleView(r.getId(), r.getName(), r.getLabel(), r.isSystem(), perms);
    }
}
