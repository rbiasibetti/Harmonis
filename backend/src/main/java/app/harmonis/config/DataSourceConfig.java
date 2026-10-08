package app.harmonis.config;

import app.harmonis.tenancy.RoutingDataSource;
import app.harmonis.tenancy.TenantConnectionProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Dois datasources:
 *  - catalogo (banco mestre {@code harmonis_catalog}) -> acesso via JdbcTemplate.
 *  - roteamento (@Primary) -> usado pelo JPA; resolve o banco do tenant a cada conexao.
 */
@Configuration
@EnableConfigurationProperties(HarmonisProperties.class)
public class DataSourceConfig {

    /** Pool fixo apontado para o banco de catalogo. */
    @Bean
    public HikariDataSource catalogDataSource(HarmonisProperties props) {
        HarmonisProperties.SqlServer db = props.getSqlserver();
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(props.jdbcUrl(props.getCatalog().getDbName()));
        cfg.setUsername(db.getUser());
        cfg.setPassword(db.getPassword());
        cfg.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        cfg.setPoolName("hm-catalog");
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        return new HikariDataSource(cfg);
    }

    @Bean
    public JdbcTemplate catalogJdbcTemplate(HikariDataSource catalogDataSource) {
        return new JdbcTemplate(catalogDataSource);
    }

    /** Datasource primario usado pelo JPA/Hibernate: roteia por tenant. */
    @Bean
    @Primary
    public DataSource dataSource(TenantConnectionProvider provider) {
        return new RoutingDataSource(provider);
    }
}
