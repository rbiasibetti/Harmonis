package app.harmonis.identity;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Semeadura idempotente de um banco de tenant: todas as permissoes, os papeis de sistema
 * (admin/manager/member) com seus mapeamentos (fonte: {@link AclCatalog}) e o usuario dono.
 *
 * O {@code TenantContext} deve estar definido ANTES de chamar estes metodos (o datasource
 * roteia por ele). Equivale ao provisionamento descrito no handoff.
 */
@Service
public class TenantSeedService {

    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public TenantSeedService(PermissionRepository permissions, RoleRepository roles,
                             UserRepository users, PasswordEncoder passwordEncoder) {
        this.permissions = permissions;
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void seedRbac() {
        // 1) permissoes
        for (String name : AclCatalog.allPermissions()) {
            permissions.findByName(name).orElseGet(() -> permissions.save(new Permission(name)));
        }
        // 2) papeis de sistema + mapeamento
        for (String roleName : AclCatalog.SYSTEM_ROLES) {
            Role role = roles.findByName(roleName)
                    .orElseGet(() -> new Role(roleName, AclCatalog.label(roleName), true));
            role.setLabel(AclCatalog.label(roleName));
            role.setSystem(true);

            Set<Permission> perms = new HashSet<>();
            for (String p : AclCatalog.permissionsFor(roleName)) {
                permissions.findByName(p).ifPresent(perms::add);
            }
            role.setPermissions(perms);
            roles.save(role);
        }
    }

    @Transactional
    public void seedOwner(String name, String email, String rawPassword) {
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        Role admin = roles.findByName(AclCatalog.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Papel admin ausente; rode seedRbac primeiro."));

        User owner = new User();
        owner.setName(name);
        owner.setEmail(email);
        owner.setJobTitle("Proprietario");
        owner.setOwner(true);
        owner.setActive(true);
        owner.setPassword(passwordEncoder.encode(rawPassword));
        owner.getRoles().add(admin);
        users.save(owner);
    }
}
