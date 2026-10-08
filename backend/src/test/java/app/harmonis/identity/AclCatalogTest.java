package app.harmonis.identity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AclCatalogTest {

    @Test
    void admin_tem_todas_as_permissoes() {
        List<String> all = AclCatalog.allPermissions();
        assertEquals(all.size(), AclCatalog.permissionsFor(AclCatalog.ROLE_ADMIN).size());
        assertTrue(AclCatalog.permissionsFor(AclCatalog.ROLE_ADMIN).containsAll(all));
    }

    @Test
    void member_tem_subconjunto_minimo() {
        List<String> member = AclCatalog.permissionsFor(AclCatalog.ROLE_MEMBER);
        assertTrue(member.contains("forms.view"));
        assertTrue(member.contains("documents.create"));
        assertFalse(member.contains("users.create"));
        assertFalse(member.contains("documents.delete"));
    }

    @Test
    void manager_nao_e_admin_mas_gere_documentos_e_paginas() {
        List<String> mgr = AclCatalog.permissionsFor(AclCatalog.ROLE_MANAGER);
        assertTrue(mgr.contains("documents.delete"));
        assertTrue(mgr.contains("pages.delete"));
        assertTrue(mgr.contains("forms.submissions"));
        assertFalse(mgr.contains("users.delete"));
        assertFalse(mgr.contains("roles.create"));
    }

    @Test
    void permissoes_sao_unicas_e_bem_formadas() {
        List<String> all = AclCatalog.allPermissions();
        assertEquals(all.size(), all.stream().distinct().count(), "nao deve haver permissoes duplicadas");
        assertTrue(all.stream().allMatch(p -> p.matches("[a-z]+\\.[a-z]+")), "formato recurso.acao");
    }

    @Test
    void papel_desconhecido_nao_recebe_permissoes() {
        assertTrue(AclCatalog.permissionsFor("inexistente").isEmpty());
    }
}
