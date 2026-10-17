package app.harmonis.identity;

import java.util.List;

/** Representacao do usuario exposta pela API (sem senha). */
public record UserDto(
        Long id,
        String name,
        String email,
        String jobTitle,
        boolean owner,
        boolean active,
        List<String> roles,
        List<String> permissions,
        String language
) {
    public static UserDto from(User u) {
        List<String> perms = u.isOwner()
                ? AclCatalog.allPermissions()
                : u.permissionNames().stream().toList();
        return new UserDto(
                u.getId(), u.getName(), u.getEmail(), u.getJobTitle(),
                u.isOwner(), u.isActive(),
                u.roleNames().stream().toList(),
                perms,
                u.getLanguage());
    }
}
