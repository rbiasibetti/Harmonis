package app.harmonis.identity;

import java.util.List;
import java.util.Map;

/**
 * Fonte unica de permissoes e papeis de sistema (porta de {@code config/acl.php}).
 * Usada pelo seeder de cada tenant e pela validacao de papeis personalizados.
 */
public final class AclCatalog {

    private AclCatalog() {}

    /** Todas as permissoes do sistema, agrupadas por recurso (apenas para exibicao/seed). */
    public static final Map<String, List<String>> GROUPS = Map.ofEntries(
            Map.entry("Usuarios", List.of("users.view", "users.create", "users.update", "users.delete")),
            Map.entry("Papeis", List.of("roles.view", "roles.create", "roles.update", "roles.delete")),
            Map.entry("Formularios", List.of("forms.view", "forms.create", "forms.update", "forms.delete", "forms.submissions")),
            Map.entry("Processos", List.of("processes.view")),
            Map.entry("Documentos", List.of("documents.view", "documents.create", "documents.update", "documents.delete")),
            Map.entry("Portal", List.of("pages.view", "pages.create", "pages.update", "pages.delete")),
            Map.entry("Integracoes", List.of("integrations.view", "integrations.create", "integrations.update",
                    "integrations.delete", "integrations.publish", "integrations.logs"))
    );

    /** Lista achatada de todas as permissoes. */
    public static List<String> allPermissions() {
        return GROUPS.values().stream().flatMap(List::stream).sorted().toList();
    }

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MANAGER = "manager";
    public static final String ROLE_MEMBER = "member";

    public static final List<String> SYSTEM_ROLES = List.of(ROLE_ADMIN, ROLE_MANAGER, ROLE_MEMBER);

    public static String label(String role) {
        return switch (role) {
            case ROLE_ADMIN -> "Administrador";
            case ROLE_MANAGER -> "Gestor";
            case ROLE_MEMBER -> "Colaborador";
            default -> role;
        };
    }

    /** Permissoes de cada papel de sistema. admin = todas. */
    public static List<String> permissionsFor(String role) {
        return switch (role) {
            case ROLE_ADMIN -> allPermissions();
            case ROLE_MANAGER -> List.of(
                    "users.view", "users.create", "users.update",
                    "roles.view",
                    "forms.view", "forms.create", "forms.update", "forms.submissions",
                    "processes.view",
                    "documents.view", "documents.create", "documents.update", "documents.delete",
                    "pages.view", "pages.create", "pages.update", "pages.delete");
            case ROLE_MEMBER -> List.of(
                    "forms.view", "processes.view",
                    "documents.view", "documents.create", "pages.view");
            default -> List.of();
        };
    }
}
