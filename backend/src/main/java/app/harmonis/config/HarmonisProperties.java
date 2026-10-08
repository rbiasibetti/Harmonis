package app.harmonis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracoes do Harmonis lidas de {@code harmonis.*} no application.yml. */
@ConfigurationProperties(prefix = "harmonis")
public class HarmonisProperties {

    private SqlServer sqlserver = new SqlServer();
    private Catalog catalog = new Catalog();
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();

    public SqlServer getSqlserver() { return sqlserver; }
    public void setSqlserver(SqlServer sqlserver) { this.sqlserver = sqlserver; }
    public Catalog getCatalog() { return catalog; }
    public void setCatalog(Catalog catalog) { this.catalog = catalog; }
    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }

    /** Monta a URL JDBC para um banco especifico na mesma instancia SQL Server. */
    public String jdbcUrl(String databaseName) {
        return "jdbc:sqlserver://" + sqlserver.host + ":" + sqlserver.port
                + ";databaseName=" + databaseName + sqlserver.options;
    }

    public static class SqlServer {
        private String host = "localhost";
        private int port = 1433;
        private String user = "sa";
        private String password = "";
        private String options = ";encrypt=true;trustServerCertificate=true";
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getOptions() { return options; }
        public void setOptions(String options) { this.options = options; }
    }

    public static class Catalog {
        private String dbName = "harmonis_catalog";
        public String getDbName() { return dbName; }
        public void setDbName(String dbName) { this.dbName = dbName; }
    }

    public static class Jwt {
        private String secret = "";
        private long ttlMinutes = 480;
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(long ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:4200";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }
}
