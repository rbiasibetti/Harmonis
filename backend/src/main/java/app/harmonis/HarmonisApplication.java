package app.harmonis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;

/**
 * Ponto de entrada da API Harmonis.
 *
 * Desabilitamos o {@link DataSourceAutoConfiguration} porque o datasource e configurado
 * manualmente ({@code app.harmonis.config.DataSourceConfig}): ha um datasource de catalogo
 * e um datasource de roteamento por tenant (um banco por cliente).
 *
 * Tambem desabilitamos o {@link SqlInitializationAutoConfiguration} (nao usamos schema.sql;
 * o schema e gerido manualmente via migrations/) — alem de evitar um ciclo de beans com o
 * datasource de roteamento.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        SqlInitializationAutoConfiguration.class
})
public class HarmonisApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarmonisApplication.class, args);
    }
}
