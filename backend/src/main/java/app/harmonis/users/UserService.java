package app.harmonis.users;

import app.harmonis.identity.Role;
import app.harmonis.identity.RoleRepository;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import app.harmonis.users.UserDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return users.findAll().stream().filter(User::isActive).count();
    }

    @Transactional(readOnly = true)
    public List<UserView> list(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return users.findAll().stream()
                .filter(u -> q.isEmpty()
                        || u.getName().toLowerCase().contains(q)
                        || u.getEmail().toLowerCase().contains(q))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(UserService::toView)
                .toList();
    }

    @Transactional
    public CreatedUser create(CreateUserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail.");
        }
        String initial = generatePassword();

        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setJobTitle(blankToNull(req.jobTitle()));
        user.setActive(true);
        user.setOwner(false);
        user.setPassword(passwordEncoder.encode(initial));
        user.setRoles(resolveRoles(req.roles()));
        users.save(user);

        return new CreatedUser(toView(user), initial);
    }

    @Transactional
    public UserView update(long id, UpdateUserRequest req, long actingUserId) {
        User user = require(id);

        if (user.isOwner() && !req.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "O proprietário não pode ser desativado.");
        }
        if (user.getId() == actingUserId && !req.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode desativar a si mesmo.");
        }

        user.setName(req.name().trim());
        user.setJobTitle(blankToNull(req.jobTitle()));
        user.setActive(req.active());
        // O proprietário mantém o papel admin independentemente do que vier.
        if (!user.isOwner()) {
            user.setRoles(resolveRoles(req.roles()));
        }
        users.save(user);
        return toView(user);
    }

    @Transactional
    public void delete(long id, long actingUserId) {
        User user = require(id);
        if (user.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "O proprietário não pode ser removido.");
        }
        if (user.getId() == actingUserId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode remover a si mesmo.");
        }
        users.delete(user);
    }

    private User require(long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    private Set<Role> resolveRoles(List<String> roleNames) {
        Set<Role> resolved = new HashSet<>();
        for (String name : roleNames) {
            Role role = roles.findByName(name)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Papel inválido: " + name));
            resolved.add(role);
        }
        return resolved;
    }

    private static UserView toView(User u) {
        return new UserView(
                u.getId(), u.getName(), u.getEmail(), u.getJobTitle(),
                u.isOwner(), u.isActive(), u.roleNames().stream().toList());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
