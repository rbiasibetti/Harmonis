package app.harmonis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracoes do Harmonis lidas de {@code harmonis.*} no application.yml. */
@ConfigurationProperties(prefix = "harmonis")
public class HarmonisProperties {

    private SqlServer sqlserver = new SqlServer();
    private Catalog catalog = new Catalog();
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Cookies cookies = new Cookies();
    private Storage storage = new Storage();

    public SqlServer getSqlserver() { return sqlserver; }
    public void setSqlserver(SqlServer sqlserver) { this.sqlserver = sqlserver; }
    public Catalog getCatalog() { return catalog; }
    public void setCatalog(Catalog catalog) { this.catalog = catalog; }
    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Cookies getCookies() { return cookies; }
    public void setCookies(Cookies cookies) { this.cookies = cookies; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

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
        /** Validade do access token (curta: o refresh token mantem a sessao). */
        private long ttlMinutes = 15;
        private long refreshTtlDays = 7;
        private long refreshAbsoluteDays = 30;
        private long refreshReuseGraceSeconds = 10;
        private String issuer = "harmonis";
        private String audience = "harmonis-web";
        public long getRefreshTtlDays() { return refreshTtlDays; }
        public void setRefreshTtlDays(long v) { this.refreshTtlDays = v; }
        public long getRefreshAbsoluteDays() { return refreshAbsoluteDays; }
        public void setRefreshAbsoluteDays(long v) { this.refreshAbsoluteDays = v; }
        public long getRefreshReuseGraceSeconds() { return refreshReuseGraceSeconds; }
        public void setRefreshReuseGraceSeconds(long v) { this.refreshReuseGraceSeconds = v; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(long ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    }

    /** Atributos dos cookies de sessao (access, refresh e XSRF). */
    public static class Cookies {
        /** true em producao (HTTPS): o navegador so envia o cookie por conexao segura. */
        private boolean secure = false;
        /** Strict | Lax | None (None exige secure=true). */
        private String sameSite = "Strict";
        /** Vazio = host da API. Defina (ex.: .empresa.com) se front e API estao em subdominios. */
        private String domain = "";
        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }
        public String getSameSite() { return sameSite; }
        public void setSameSite(String sameSite) { this.sameSite = sameSite; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:4200";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Storage {
        private String documentsDir = "storage/documents";
        public String getDocumentsDir() { return documentsDir; }
        public void setDocumentsDir(String documentsDir) { this.documentsDir = documentsDir; }
    }
}
