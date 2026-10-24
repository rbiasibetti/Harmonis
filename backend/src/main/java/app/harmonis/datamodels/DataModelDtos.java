package app.harmonis.datamodels;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public final class DataModelDtos {

    private DataModelDtos() {}

    /** Coluna do modelo: tipo guia o operador de filtro (text=contém; number/date=igual). */
    public record ColumnSpec(String key, String label, String type) {}

    /** Campo de consulta (filtro): referencia uma coluna pelo key; type herdado da coluna. */
    public record FilterSpec(String key, String label, String type) {}

    public record SaveDataModelRequest(
            @NotBlank String name,
            String description,
            String sourceType,            // static | query
            String querySql,              // obrigatório p/ query
            List<ColumnSpec> columns,
            List<FilterSpec> filters,
            String labelKey) {}

    public record DataModelSummary(
            Long id, String name, String description, String sourceType,
            String status, int columns, long rows) {}

    public record DataModelView(
            Long id, String name, String description, String sourceType, String querySql,
            List<ColumnSpec> columns, List<FilterSpec> filters, String labelKey,
            String status, long rows) {}

    /** Resumo para o construtor/preenchimento de formularios (somente modelos publicados). */
    public record DataModelOption(
            Long id, String name, List<ColumnSpec> columns, List<FilterSpec> filters, String labelKey) {}

    /** Consulta no preenchimento: valores dos filtros + paginacao. */
    public record QueryRequest(Map<String, Object> filters, Integer page, Integer size) {}

    public record QueryResult(
            List<Map<String, Object>> rows, int page, int size, long total, boolean capped) {}

    /** Substitui todas as linhas (edicao manual). */
    public record RowsRequest(List<Map<String, Object>> rows) {}

    public record ImportResult(int imported) {}
}
