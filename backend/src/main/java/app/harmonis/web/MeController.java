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

    public record LanguageRequest(@jakarta.validation.constraints.NotBlank String language) {}

    /** O proprio usuario define o idioma preferido da interface. */
    @org.springframework.web.bind.annotation.PutMapping("/me/language")
    public UserDto setLanguage(@AuthenticationPrincipal AuthPrincipal principal,
                               @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody LanguageRequest req) {
        String lang = app.harmonis.identity.Languages.requireSupported(req.language());
        if (lang == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idioma inválido.");
        var user = users.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado."));
        user.setLanguage(lang);
        users.save(user);
        return UserDto.from(user);
    }

    /** Dados do usuario autenticado (resolve o tenant pelo JWT via filtro). */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthPrincipal principal) {
        return users.findById(principal.userId())
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado."));
    }
}
