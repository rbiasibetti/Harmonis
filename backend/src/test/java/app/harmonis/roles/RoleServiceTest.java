package app.harmonis.roles;

import app.harmonis.identity.Permission;
import app.harmonis.identity.PermissionRepository;
import app.harmonis.identity.Role;
import app.harmonis.identity.RoleRepository;
import app.harmonis.roles.RoleDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoleServiceTest {

    private RoleRepository roles;
    private PermissionRepository permissions;
    private RoleService service;

    @BeforeEach
    void setUp() {
        roles = mock(RoleRepository.class);
        permissions = mock(PermissionRepository.class);
        service = new RoleService(roles, permissions);
        when(permissions.findByName(any())).thenAnswer(inv -> Optional.of(new Permission(inv.getArgument(0))));
    }

    @Test
    void cria_papel_personalizado_normalizando_nome() {
        when(roles.findByName("lider_de_equipe")).thenReturn(Optional.empty());

        RoleView view = service.create(new SaveRoleRequest("Líder de Equipe", "Líder", List.of("forms.view")));

        ArgumentCaptor<Role> saved = ArgumentCaptor.forClass(Role.class);
        verify(roles).save(saved.capture());
        assertEquals("lider_de_equipe", saved.getValue().getName());
        assertFalse(saved.getValue().isSystem());
        assertTrue(view.permissions().contains("forms.view"));
    }

    @Test
    void nome_de_sistema_e_rejeitado() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new SaveRoleRequest("admin", "Admin", List.of())));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(roles, never()).save(any());
    }

    @Test
    void nome_duplicado_e_rejeitado() {
        when(roles.findByName("suporte")).thenReturn(Optional.of(new Role("suporte", "Suporte", false)));
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new SaveRoleRequest("suporte", "Suporte", List.of())));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void papel_de_sistema_nao_pode_ser_editado() {
        Role admin = new Role("admin", "Administrador", true);
        when(roles.findById(1L)).thenReturn(Optional.of(admin));
        var ex = assertThrows(ResponseStatusException.class, () -> service.update(
                1L, new SaveRoleRequest("admin", "Admin", List.of("users.view"))));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void papel_de_sistema_nao_pode_ser_removido() {
        Role manager = new Role("manager", "Gestor", true);
        when(roles.findById(2L)).thenReturn(Optional.of(manager));
        var ex = assertThrows(ResponseStatusException.class, () -> service.delete(2L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(roles, never()).delete(any());
    }

    @Test
    void permissao_invalida_gera_bad_request() {
        when(roles.findByName("qa")).thenReturn(Optional.empty());
        when(permissions.findByName("inexistente.acao")).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new SaveRoleRequest("qa", "QA", List.of("inexistente.acao"))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void catalogo_de_permissoes_vem_agrupado() {
        PermissionCatalog cat = service.catalog();
        assertFalse(cat.groups().isEmpty());
        assertTrue(cat.groups().stream().anyMatch(g -> g.group().equals("Usuarios")));
    }
}
