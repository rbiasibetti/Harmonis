package app.harmonis.security;

import app.harmonis.auth.AuthCookies;
import app.harmonis.config.HarmonisProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo HTTP real da cadeia de seguranca com sessao em cookies: 401 sem sessao, token so no
 * cookie (nunca no header), CSRF double-submit nas mutacoes e — regressao — o cookie XSRF-TOKEN
 * jamais pode ser apagado/zerado por respostas autenticadas.
 */
@WebMvcTest(controllers = SessionCookieSecurityTest.PingController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, AuthCookies.class,
        SessionCookieSecurityTest.Props.class, SessionCookieSecurityTest.PingController.class})
@TestPropertySource(properties = {
        "harmonis.jwt.secret=chave-de-teste-com-pelo-menos-32-bytes-aqui!!",
        "harmonis.cors.allowed-origins=http://localhost:4200"
})
class SessionCookieSecurityTest {

    @EnableConfigurationProperties(HarmonisProperties.class)
    static class Props {}

    @RestController
    @RequestMapping("/api/ping")
    static class PingController {
        @GetMapping String read() { return "ok"; }
        @PostMapping String write() { return "ok"; }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    private Cookie accessCookie() {
        return new Cookie(AuthCookies.ACCESS, jwt.issue(1L, "acme", false, List.of("member"), List.of("forms.view")));
    }

    private static List<String> setCookies(MvcResult r) {
        return r.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }

    @Test
    void semSessaoRetorna401() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenNoHeaderAuthorizationNaoAutentica() throws Exception {
        String token = jwt.issue(1L, "acme", false, List.of(), List.of());
        mvc.perform(get("/api/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cookieDeAcessoAutenticaLeituras() throws Exception {
        mvc.perform(get("/api/ping").cookie(accessCookie())).andExpect(status().isOk());
    }

    @Test
    void mutacaoSemTokenCsrfEhNegada() throws Exception {
        mvc.perform(post("/api/ping").cookie(accessCookie())).andExpect(status().isForbidden());
    }

    @Test
    void mutacaoComTokenCsrfIgualAoCookieEhAceita() throws Exception {
        mvc.perform(post("/api/ping").cookie(accessCookie(), new Cookie("XSRF-TOKEN", "abc-123"))
                        .header("X-XSRF-TOKEN", "abc-123"))
                .andExpect(status().isOk());
    }

    @Test
    void tokenCsrfDivergenteEhNegado() throws Exception {
        mvc.perform(post("/api/ping").cookie(accessCookie(), new Cookie("XSRF-TOKEN", "abc-123"))
                        .header("X-XSRF-TOKEN", "outro"))
                .andExpect(status().isForbidden());
    }

    /** Regressao: a estrategia de sessao do CSRF zerava o cookie a cada resposta autenticada. */
    @Test
    void respostasAutenticadasNaoApagamOCookieXsrf() throws Exception {
        for (var req : List.of(
                get("/api/ping").cookie(accessCookie(), new Cookie("XSRF-TOKEN", "abc-123")),
                post("/api/ping").cookie(accessCookie(), new Cookie("XSRF-TOKEN", "abc-123")).header("X-XSRF-TOKEN", "abc-123"))) {
            MvcResult r = mvc.perform(req).andExpect(status().isOk()).andReturn();
            for (String c : setCookies(r)) {
                if (c.startsWith("XSRF-TOKEN=")) {
                    String value = c.substring("XSRF-TOKEN=".length()).split(";", 2)[0];
                    assertFalse(value.isEmpty(), "cookie XSRF-TOKEN foi zerado: " + c);
                }
            }
        }
    }

    @Test
    void semCookieXsrfOServidorEmiteUmNovoLegivelPeloJs() throws Exception {
        MvcResult r = mvc.perform(get("/api/ping").cookie(accessCookie())).andExpect(status().isOk()).andReturn();
        String xsrf = setCookies(r).stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElse(null);
        assertNotNull(xsrf, "cookie XSRF-TOKEN deveria ser emitido");
        assertFalse(xsrf.startsWith("XSRF-TOKEN=;"), xsrf);
        assertFalse(xsrf.contains("HttpOnly"), "o JS precisa ler o cookie XSRF para o double-submit");
    }
}
