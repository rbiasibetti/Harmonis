package app.harmonis.datamodels;

import app.harmonis.datamodels.DataModelDtos.*;
import app.harmonis.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datamodels")
public class DataModelController {

    private final DataModelService service;
    private final DataModelQueryService queries;

    public DataModelController(DataModelService service, DataModelQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('datamodels.view')")
    public List<DataModelSummary> list() { return service.list(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('datamodels.view')")
    public DataModelView get(@PathVariable long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('datamodels.create')")
    public DataModelView create(@Valid @RequestBody SaveDataModelRequest req,
                                @AuthenticationPrincipal AuthPrincipal principal) {
        return service.create(req, principal.userId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('datamodels.update')")
    public DataModelView update(@PathVariable long id, @Valid @RequestBody SaveDataModelRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('datamodels.delete')")
    public void delete(@PathVariable long id) { service.delete(id); }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('datamodels.publish')")
    public DataModelView publish(@PathVariable long id) { return service.setPublished(id, true); }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('datamodels.publish')")
    public DataModelView unpublish(@PathVariable long id) { return service.setPublished(id, false); }

    // ----- linhas (static) -----

    @GetMapping("/{id}/rows")
    @PreAuthorize("hasAuthority('datamodels.view')")
    public List<Map<String, Object>> rows(@PathVariable long id) { return service.rows(id); }

    @PutMapping("/{id}/rows")
    @PreAuthorize("hasAuthority('datamodels.update')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceRows(@PathVariable long id, @RequestBody RowsRequest req) {
        service.replaceRows(id, req == null ? null : req.rows());
    }

    @PostMapping("/{id}/import")
    @PreAuthorize("hasAuthority('datamodels.update')")
    public ImportResult importCsv(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        return service.importCsv(id, file);
    }

    // ----- uso nos formularios -----

    /** Lista de modelos publicados para vincular no construtor (quem edita formularios também vê). */
    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('datamodels.view','forms.create','forms.update')")
    public List<DataModelOption> options() { return service.options(); }

    /** Definicao (colunas/filtros) de um modelo, para montar o modal de busca no preenchimento. */
    @GetMapping("/{id}/option")
    @PreAuthorize("hasAuthority('forms.view')")
    public DataModelOption option(@PathVariable long id) { return service.option(id); }

    /** Consulta no preenchimento: filtros + paginacao. */
    @PostMapping("/{id}/query")
    @PreAuthorize("hasAuthority('forms.view')")
    public QueryResult query(@PathVariable long id, @RequestBody(required = false) QueryRequest req) {
        return queries.query(id, req);
    }
}
