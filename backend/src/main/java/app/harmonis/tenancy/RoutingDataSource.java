package app.harmonis.tenancy;

import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Datasource "primario" usado pelo JPA. A cada conexao solicitada, delega para o pool
 * do tenant atual ({@link TenantContext}). Como o dialeto esta fixado no application.yml,
 * o Hibernate nao abre conexao no startup — so quando ha um tenant no contexto.
 */
public class RoutingDataSource extends AbstractDataSource {

    private final TenantConnectionProvider provider;

    public RoutingDataSource(TenantConnectionProvider provider) {
        this.provider = provider;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return provider.dataSourceFor(TenantContext.require()).getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return provider.dataSourceFor(TenantContext.require()).getConnection(username, password);
    }
}
