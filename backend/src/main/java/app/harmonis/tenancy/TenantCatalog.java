package app.harmonis.tenancy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Acesso ao banco mestre (catalogo). Le e escreve o registro de tenants em {@code hm_tenants}.
 * Usa um {@link JdbcTemplate} apontado para o datasource de catalogo (NAO o de roteamento).
 */
@Service
public class TenantCatalog {

    private final JdbcTemplate jdbc;

    public TenantCatalog(@Qualifier("catalogJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLUMNS =
            "id, name, slug, db_name, [plan], is_active";

    private static TenantInfo map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new TenantInfo(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("db_name"),
                rs.getString("plan"),
                rs.getBoolean("is_active")
        );
    }

    public Optional<TenantInfo> findBySlug(String slug) {
        List<TenantInfo> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM hm_tenants WHERE slug = ?",
                TenantCatalog::map, slug);
        return rows.stream().findFirst();
    }

    public List<TenantInfo> findAllActive() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM hm_tenants WHERE is_active = 1 ORDER BY name",
                TenantCatalog::map);
    }
}
