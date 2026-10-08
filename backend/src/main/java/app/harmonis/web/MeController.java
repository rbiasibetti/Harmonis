package app.harmonis.web;

import app.harmonis.identity.UserDto;
import app.harmonis.identity.UserRepository;
import app.harmonis.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    /** Dados do usuario autenticado (resolve o tenant pelo JWT via filtro). */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthPrincipal principal) {
        return users.findById(principal.userId())
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado."));
    }
}
