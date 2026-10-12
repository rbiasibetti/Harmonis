package app.harmonis.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Repositorios do modulo de documentos (agrupados). */
public final class DocumentRepositories {
    private DocumentRepositories() {}
}

interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByParentIdOrderByNameAsc(Long parentId);
    List<Folder> findByParentId(Long parentId);
}

interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByFolderIdOrderByNameAsc(Long folderId);
    List<Document> findByFolderId(Long folderId);
}

interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByDocumentIdOrderByVersionDesc(Long documentId);
    Optional<DocumentVersion> findByDocumentIdAndVersion(Long documentId, int version);
}
