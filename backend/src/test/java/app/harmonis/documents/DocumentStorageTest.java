package app.harmonis.documents;

import app.harmonis.config.HarmonisProperties;
import app.harmonis.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageTest {

    private DocumentStorage storage;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        HarmonisProperties props = new HarmonisProperties();
        props.getStorage().setDocumentsDir(tmp.toString());
        storage = new DocumentStorage(props);
        TenantContext.set("acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void armazena_sob_a_pasta_do_tenant_e_le_de_volta() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contrato.pdf", "application/pdf", "conteudo".getBytes(StandardCharsets.UTF_8));

        DocumentStorage.Stored stored = storage.store(file);

        assertTrue(stored.path().startsWith("acme/"));
        assertEquals("contrato.pdf", stored.originalName());
        assertEquals("application/pdf", stored.mimeType());
        assertEquals(8, stored.size());

        Path resolved = storage.resolve(stored.path());
        assertTrue(Files.exists(resolved));
        assertEquals("conteudo", Files.readString(resolved));
    }

    @Test
    void arquivo_vazio_e_rejeitado() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.txt", "text/plain", new byte[0]);
        assertThrows(ResponseStatusException.class, () -> storage.store(empty));
    }

    @Test
    void resolve_fora_da_raiz_e_bloqueado() {
        assertThrows(ResponseStatusException.class, () -> storage.resolve("../../etc/passwd"));
    }

    @Test
    void delete_remove_o_arquivo() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "nota.txt", "text/plain", "x".getBytes());
        DocumentStorage.Stored stored = storage.store(file);
        Path p = storage.resolve(stored.path());
        assertTrue(Files.exists(p));
        storage.delete(stored.path());
        assertFalse(Files.exists(p));
    }
}
