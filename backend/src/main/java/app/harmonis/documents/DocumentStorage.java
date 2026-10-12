package app.harmonis.documents;

import app.harmonis.config.HarmonisProperties;
import app.harmonis.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * Armazenamento de arquivos de documentos em disco privado (nunca servidos diretamente).
 * Estrutura: {root}/{tenant}/{ulid}.{ext}. O caminho relativo (tenant/arquivo) e guardado no banco.
 */
@Component
public class DocumentStorage {

    private final Path root;

    public DocumentStorage(HarmonisProperties props) {
        this.root = Paths.get(props.getStorage().getDocumentsDir()).toAbsolutePath().normalize();
    }

    public record Stored(String path, String originalName, String mimeType, long size) {}

    public Stored store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo vazio.");
        }
        String tenant = TenantContext.require();
        String original = sanitizeName(file.getOriginalFilename());
        String ext = extensionOf(original);
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        String relative = tenant + "/" + stored;

        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caminho inválido.");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao salvar o arquivo.");
        }
        return new Stored(relative, original, file.getContentType(), file.getSize());
    }

    public Path resolve(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caminho inválido.");
        }
        return p;
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
            // melhor-esforco: registro removido mesmo que o arquivo persista
        }
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "arquivo";
        return Paths.get(name).getFileName().toString();
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0 && dot < name.length() - 1) ? name.substring(dot).toLowerCase() : "";
    }
}
