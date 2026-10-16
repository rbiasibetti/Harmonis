package app.harmonis.integrations;

import app.harmonis.integrations.IntegrationDtos.AuthSpec;
import app.harmonis.integrations.IntegrationDtos.HeaderSpec;

import java.util.List;

/** Configuracao de uma integracao ja DECIFRADA, usada apenas em memoria no envio. */
public record IntegrationRuntime(
        Long id,
        String url,
        String method,
        AuthSpec auth,
        List<HeaderSpec> headers,
        String signingSecret,
        int timeoutSeconds
) {}
