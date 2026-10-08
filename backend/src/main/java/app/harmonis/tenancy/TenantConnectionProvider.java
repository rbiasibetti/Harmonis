package app.harmonis.tenancy;

import app.harmonis.config.HarmonisProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mantem um pool de conexoes (Hikari) por tenant, criado sob demanda a partir do catalogo.
 * Cada tenant aponta para um banco distinto na mesma instancia SQL Server (db-per-tenant).
 */
@Component
public class TenantConnectionProvider {

    private final HarmonisProperties props;
    private final TenantCatalog catalog;
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    public TenantConnectionProvider(HarmonisProperties props, TenantCatalog catalog) {
        this.props = props;
        this.catalog = catalog;
    }

    public DataSource dataSourceFor(String tenantSlug) {
        return pools.computeIfAbsent(tenantSlug, slug -> {
            TenantInfo tenant = catalog.findBySlug(slug)
                    .filter(TenantInfo::active)
                    .orElseThrow(() -> new IllegalStateException(
                            "Tenant desconhecido ou inativo: " + slug));
            return build(tenant);
        });
    }

    private HikariDataSource build(TenantInfo tenant) {
        HarmonisProperties.SqlServer db = props.getSqlserver();
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(props.jdbcUrl(tenant.dbName()));
        cfg.setUsername(db.getUser());
        cfg.setPassword(db.getPassword());
        cfg.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        cfg.setPoolName("hm-" + tenant.slug());
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(1);
        return new HikariDataSource(cfg);
    }

    /** Fecha todos os pools (ex.: shutdown). */
    public void shutdown() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
