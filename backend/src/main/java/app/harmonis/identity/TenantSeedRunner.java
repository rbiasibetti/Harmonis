package app.harmonis.identity;

import app.harmonis.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Semeia um tenant quando a aplicacao sobe com {@code --harmonis.seed.tenant=<slug>}.
 * Ex.: {@code java -jar harmonis-backend.jar --harmonis.seed.tenant=acme}
 * As tabelas ja devem existir (migrations/tenant/*). Idempotente.
 */
@Component
public class TenantSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantSeedRunner.class);

    private final TenantSeedService seed;

    public TenantSeedRunner(TenantSeedService seed) {
        this.seed = seed;
    }

    @Override
    public void run(ApplicationArguments args) {
        String tenant = firstValue(args, "harmonis.seed.tenant");
        if (tenant == null || tenant.isBlank()) {
            return;
        }
        String ownerName = orDefault(firstValue(args, "harmonis.seed.owner-name"), "Administrador");
        String ownerEmail = orDefault(firstValue(args, "harmonis.seed.owner-email"), "admin@" + tenant + ".test");
        String ownerPass = orDefault(firstValue(args, "harmonis.seed.owner-password"), "password");

        TenantContext.set(tenant);
        try {
            log.info("Semeando RBAC + dono no tenant '{}'...", tenant);
            seed.seedRbac();
            seed.seedOwner(ownerName, ownerEmail, ownerPass);
            log.info("Seed concluido: dono {} / papeis {}", ownerEmail, AclCatalog.SYSTEM_ROLES);
        } finally {
            TenantContext.clear();
        }
    }

    private static String firstValue(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private static String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}
