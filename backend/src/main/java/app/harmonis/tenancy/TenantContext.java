package app.harmonis.tenancy;

/**
 * Guarda o tenant atual (slug) por thread da requisicao.
 *
 * Preenchido pelo filtro de autenticacao (claim do JWT ou header de desenvolvimento)
 * e lido pelo {@code RoutingDataSource} para escolher o banco do cliente.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantSlug) {
        CURRENT.set(tenantSlug);
    }

    public static String get() {
        return CURRENT.get();
    }

    /** Retorna o tenant atual ou lanca se nenhum estiver definido (acesso a dados sem tenant e sempre bug). */
    public static String require() {
        String slug = CURRENT.get();
        if (slug == null || slug.isBlank()) {
            throw new IllegalStateException("Nenhum tenant no contexto da requisicao.");
        }
        return slug;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
