package app.harmonis.users;

import app.harmonis.security.AuthPrincipal;
import app.harmonis.users.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users.view')")
    public List<UserView> list(@RequestParam(name = "q", required = false) String query) {
        return service.list(query);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('users.create')")
    public CreatedUser create(@Valid @RequestBody CreateUserRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('users.update')")
    public UserView update(@PathVariable long id,
                           @Valid @RequestBody UpdateUserRequest req,
                           @AuthenticationPrincipal AuthPrincipal principal) {
        return service.update(id, req, principal.userId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('users.delete')")
    public void delete(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal principal) {
        service.delete(id, principal.userId());
    }
}
