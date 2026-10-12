package app.harmonis.documents;

import app.harmonis.documents.DocumentDtos.*;
import app.harmonis.identity.User;
import app.harmonis.identity.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class DocumentService {

    private final FolderRepository folders;
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final UserRepository users;
    private final DocumentStorage storage;
    private final ObjectMapper mapper;

    DocumentService(FolderRepository folders, DocumentRepository documents,
                    DocumentVersionRepository versions, UserRepository users,
                    DocumentStorage storage, ObjectMapper mapper) {
        this.folders = folders;
        this.documents = documents;
        this.versions = versions;
        this.users = users;
        this.storage = storage;
        this.mapper = mapper;
    }

    // ----- navegacao -----

    @Transactional(readOnly = true)
    public BrowseView browse(Long folderId) {
        Folder current = folderId == null ? null : requireFolder(folderId);
        List<FolderRef> breadcrumb = breadcrumb(current);

        List<FolderView> subfolders = folders.findByParentIdOrderByNameAsc(folderId).stream()
                .map(f -> new FolderView(f.getId(), f.getName(), f.getCreatedAt())).toList();
        List<DocumentView> docs = documents.findByFolderIdOrderByNameAsc(folderId).stream()
                .map(this::toView).toList();

        FolderRef currentRef = current == null ? null : new FolderRef(current.getId(), current.getName());
        return new BrowseView(currentRef, breadcrumb, subfolders, docs);
    }

    private List<FolderRef> breadcrumb(Folder current) {
        LinkedList<FolderRef> trail = new LinkedList<>();
        Folder f = current;
        Set<Long> guard = new HashSet<>();
        while (f != null && guard.add(f.getId())) {
            trail.addFirst(new FolderRef(f.getId(), f.getName()));
            f = f.getParentId() == null ? null : folders.findById(f.getParentId()).orElse(null);
        }
        return trail;
    }

    // ----- pastas -----

    @Transactional
    public FolderView createFolder(String name, Long parentId, Long userId) {
        if (parentId != null) requireFolder(parentId);
        Folder folder = new Folder();
        folder.setName(name.trim());
        folder.setParentId(parentId);
        folder.setCreatedBy(userId);
        folders.save(folder);
        return new FolderView(folder.getId(), folder.getName(), folder.getCreatedAt());
    }

    @Transactional
    public FolderView renameFolder(long id, String name) {
        Folder folder = requireFolder(id);
        folder.setName(name.trim());
        folders.save(folder);
        return new FolderView(folder.getId(), folder.getName(), folder.getCreatedAt());
    }

    /** Exclui a pasta e todo o seu conteudo (subpastas e documentos) recursivamente. */
    @Transactional
    public void deleteFolder(long id) {
        requireFolder(id);
        List<Long> toVisit = new ArrayList<>(List.of(id));
        List<Long> allFolders = new ArrayList<>();
        while (!toVisit.isEmpty()) {
            Long fid = toVisit.remove(toVisit.size() - 1);
            allFolders.add(fid);
            folders.findByParentId(fid).forEach(c -> toVisit.add(c.getId()));
        }
        // documentos de todas as pastas
        for (Long fid : allFolders) {
            for (Document doc : documents.findByFolderId(fid)) {
                deleteDocumentFiles(doc.getId());
                documents.delete(doc);
            }
        }
        // pastas (folhas primeiro)
        Collections.reverse(allFolders);
        allFolders.forEach(folders::deleteById);
    }

    // ----- documentos -----

    @Transactional
    public DocumentDetail upload(Long folderId, MultipartFile file, String name, String description, Long userId) {
        if (folderId != null) requireFolder(folderId);
        DocumentStorage.Stored stored = storage.store(file);

        Document doc = new Document();
        doc.setFolderId(folderId);
        doc.setName((name == null || name.isBlank()) ? stored.originalName() : name.trim());
        doc.setDescription(blankToNull(description));
        doc.setCurrentVersion(1);
        doc.setCreatedBy(userId);
        documents.save(doc);

        saveVersion(doc.getId(), 1, stored, userId);
        return detail(doc.getId());
    }

    @Transactional
    public DocumentDetail addVersion(long documentId, MultipartFile file, Long userId) {
        Document doc = requireDocument(documentId);
        DocumentStorage.Stored stored = storage.store(file);
        int next = doc.getCurrentVersion() + 1;
        doc.setCurrentVersion(next);
        documents.save(doc);
        saveVersion(doc.getId(), next, stored, userId);
        return detail(doc.getId());
    }

    @Transactional
    public DocumentDetail updateDocument(long id, String name, String description, Map<String, String> metadata) {
        Document doc = requireDocument(id);
        doc.setName(name.trim());
        doc.setDescription(blankToNull(description));
        doc.setMetadataJson(writeMetadata(metadata));
        documents.save(doc);
        return detail(id);
    }

    @Transactional
    public void deleteDocument(long id) {
        requireDocument(id);
        deleteDocumentFiles(id);
        documents.deleteById(id); // cascade remove as versions no banco
    }

    @Transactional(readOnly = true)
    public DocumentDetail detail(long id) {
        Document doc = requireDocument(id);
        Map<Long, String> names = userNames();
        List<VersionView> vs = versions.findByDocumentIdOrderByVersionDesc(id).stream()
                .map(v -> new VersionView(v.getId(), v.getVersion(), v.getOriginalName(),
                        v.getMimeType(), v.getSize(),
                        v.getUploadedBy() == null ? null : names.get(v.getUploadedBy()),
                        v.getCreatedAt()))
                .toList();
        return new DocumentDetail(doc.getId(), doc.getFolderId(), doc.getName(), doc.getDescription(),
                doc.getCurrentVersion(), readMetadata(doc.getMetadataJson()), vs);
    }

    // ----- download -----

    public record Downloadable(java.nio.file.Path path, String originalName, String mimeType) {}

    @Transactional(readOnly = true)
    public Downloadable download(long documentId) {
        Document doc = requireDocument(documentId);
        DocumentVersion v = versions.findByDocumentIdAndVersion(doc.getId(), doc.getCurrentVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versão não encontrada."));
        return toDownloadable(v);
    }

    @Transactional(readOnly = true)
    public Downloadable downloadVersion(long versionId) {
        DocumentVersion v = versions.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versão não encontrada."));
        return toDownloadable(v);
    }

    private Downloadable toDownloadable(DocumentVersion v) {
        return new Downloadable(storage.resolve(v.getPath()), v.getOriginalName(), v.getMimeType());
    }

    // ----- busca -----

    @Transactional(readOnly = true)
    public List<SearchHit> search(String term) {
        String q = term == null ? "" : term.trim().toLowerCase();
        if (q.isEmpty()) return List.of();

        Map<Long, String> folderNames = new HashMap<>();
        folders.findAll().forEach(f -> folderNames.put(f.getId(), f.getName()));

        List<SearchHit> hits = new ArrayList<>();
        for (Document doc : documents.findAll()) {
            if (matches(doc, q)) {
                hits.add(new SearchHit(toView(doc), doc.getFolderId(),
                        doc.getFolderId() == null ? null : folderNames.get(doc.getFolderId())));
            }
        }
        hits.sort(Comparator.comparing(h -> h.document().name(), String.CASE_INSENSITIVE_ORDER));
        return hits;
    }

    private boolean matches(Document doc, String q) {
        if (contains(doc.getName(), q) || contains(doc.getDescription(), q)) return true;
        for (Map.Entry<String, String> e : readMetadata(doc.getMetadataJson()).entrySet()) {
            if (contains(e.getKey(), q) || contains(e.getValue(), q)) return true;
        }
        for (DocumentVersion v : versions.findByDocumentIdOrderByVersionDesc(doc.getId())) {
            if (contains(v.getOriginalName(), q)) return true;
        }
        return false;
    }

    public long countDocuments() {
        return documents.count();
    }

    // ----- helpers -----

    private void saveVersion(Long documentId, int version, DocumentStorage.Stored stored, Long userId) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(documentId);
        v.setVersion(version);
        v.setDisk("local");
        v.setPath(stored.path());
        v.setOriginalName(stored.originalName());
        v.setMimeType(stored.mimeType());
        v.setSize(stored.size());
        v.setUploadedBy(userId);
        versions.save(v);
    }

    private void deleteDocumentFiles(Long documentId) {
        for (DocumentVersion v : versions.findByDocumentIdOrderByVersionDesc(documentId)) {
            storage.delete(v.getPath());
        }
    }

    private DocumentView toView(Document doc) {
        return new DocumentView(doc.getId(), doc.getName(), doc.getDescription(), doc.getCurrentVersion(),
                readMetadata(doc.getMetadataJson()), doc.getCreatedAt(), doc.getUpdatedAt());
    }

    private Folder requireFolder(long id) {
        return folders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pasta não encontrada."));
    }

    private Document requireDocument(long id) {
        return documents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
    }

    private Map<Long, String> userNames() {
        Map<Long, String> map = new HashMap<>();
        users.findAll().forEach(u -> map.put(u.getId(), u.getName()));
        return map;
    }

    private Map<String, String> readMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private String writeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        Map<String, String> clean = new LinkedHashMap<>();
        metadata.forEach((k, v) -> { if (k != null && !k.isBlank()) clean.put(k.trim(), v == null ? "" : v); });
        if (clean.isEmpty()) return null;
        try { return mapper.writeValueAsString(clean); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadados inválidos."); }
    }

    private static boolean contains(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase().contains(needleLower);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
