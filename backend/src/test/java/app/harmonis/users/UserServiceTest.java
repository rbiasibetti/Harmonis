package app.harmonis.users;

import app.harmonis.identity.Role;
import app.harmonis.identity.RoleRepository;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import app.harmonis.users.UserDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository users;
    private RoleRepository roles;
    private PasswordEncoder encoder;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hashed");
        service = new UserService(users, roles, encoder);
    }

    private Role role(String name) {
        return new Role(name, name, true);
    }

    @Test
    void cria_usuario_com_senha_inicial_gerada() {
        when(users.findByEmailIgnoreCase("joao@acme.test")).thenReturn(Optional.empty());
        when(roles.findByName("member")).thenReturn(Optional.of(role("member")));

        CreatedUser result = service.create(new CreateUserRequest(
                "João", "joao@acme.test", "Analista", List.of("member")));

        assertNotNull(result.initialPassword());
        assertEquals(10, result.initialPassword().length());
        assertEquals("joao@acme.test", result.user().email());
        assertFalse(result.user().owner());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("hashed", saved.getValue().getPassword());
    }

    @Test
    void email_duplicado_gera_conflito() {
        when(users.findByEmailIgnoreCase("dup@acme.test")).thenReturn(Optional.of(new User()));
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new CreateUserRequest("Dup", "dup@acme.test", null, List.of())));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(users, never()).save(any());
    }

    @Test
    void papel_invalido_gera_bad_request() {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(roles.findByName("fantasma")).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new CreateUserRequest("X", "x@acme.test", null, List.of("fantasma"))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void nao_desativa_o_proprietario() {
        User owner = new User();
        owner.setName("Dono"); owner.setEmail("dono@acme.test"); owner.setOwner(true); owner.setActive(true);
        when(users.findById(1L)).thenReturn(Optional.of(owner));

        var ex = assertThrows(ResponseStatusException.class, () -> service.update(
                1L, new UpdateUserRequest("Dono", null, List.of(), false), 99L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void nao_desativa_a_si_mesmo() throws Exception {
        User self = new User();
        self.setName("Eu"); self.setEmail("eu@acme.test"); self.setActive(true);
        setId(self, 5L);
        when(users.findById(5L)).thenReturn(Optional.of(self));

        var ex = assertThrows(ResponseStatusException.class, () -> service.update(
                5L, new UpdateUserRequest("Eu", null, List.of(), false), 5L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    private static void setId(User u, long id) throws Exception {
        var field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(u, id);
    }

    @Test
    void nao_remove_o_proprietario() {
        User owner = new User();
        owner.setOwner(true); owner.setName("Dono"); owner.setEmail("d@acme.test");
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        var ex = assertThrows(ResponseStatusException.class, () -> service.delete(1L, 2L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(users, never()).delete(any());
    }
}
