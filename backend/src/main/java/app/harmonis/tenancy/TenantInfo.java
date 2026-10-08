package app.harmonis.tenancy;

/** Registro de um tenant no banco de catalogo ({@code hm_tenants}). */
public record TenantInfo(
        long id,
        String name,
        String slug,
        String dbName,
        String plan,
        boolean active
) {}
