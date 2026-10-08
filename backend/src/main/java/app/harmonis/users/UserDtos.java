package app.harmonis.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** DTOs de entrada/saida do modulo de Usuarios. */
public final class UserDtos {

    private UserDtos() {}

    /** Representacao de usuario em listagens/detalhe (sem senha). */
    public record UserView(
            Long id,
            String name,
            String email,
            String jobTitle,
            boolean owner,
            boolean active,
            List<String> roles
    ) {}

    public record CreateUserRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            String jobTitle,
            @NotNull List<String> roles
    ) {}

    public record UpdateUserRequest(
            @NotBlank String name,
            String jobTitle,
            @NotNull List<String> roles,
            boolean active
    ) {}

    /** Retorno da criacao: inclui a senha inicial gerada (exibida uma unica vez). */
    public record CreatedUser(UserView user, String initialPassword) {}
}
