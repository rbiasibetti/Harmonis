package app.harmonis.auth;

import app.harmonis.config.HarmonisProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookies de sessao. Os dois tokens sao HttpOnly (JavaScript nao os le, o que neutraliza o roubo por XSS):
 * o access token so e enviado a /api; o refresh token so a /api/auth.
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "hm_at";
    public static final String REFRESH = "hm_rt";
    public static final String REFRESH_PATH = "/api/auth";

    private final HarmonisProperties.Cookies cfg;

    public AuthCookies(HarmonisProperties props) {
        this.cfg = props.getCookies();
        if ("None".equalsIgnoreCase(cfg.getSameSite()) && !cfg.isSecure()) {
            throw new IllegalStateException("harmonis.cookies.same-site=None exige harmonis.cookies.secure=true.");
        }
    }

    public void setAccess(HttpServletResponse res, String jwt, long ttlSeconds) {
        add(res, build(ACCESS, jwt, "/api", Duration.ofSeconds(ttlSeconds)));
    }

    public void setRefresh(HttpServletResponse res, String token, Duration maxAge) {
        add(res, build(REFRESH, token, REFRESH_PATH, maxAge));
    }

    public void clear(HttpServletResponse res) {
        add(res, build(ACCESS, "", "/api", Duration.ZERO));
        add(res, build(REFRESH, "", REFRESH_PATH, Duration.ZERO));
    }

    public static String read(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) return c.getValue();
        }
        return null;
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cfg.isSecure())
                .sameSite(cfg.getSameSite())
                .path(path)
                .maxAge(maxAge);
        if (cfg.getDomain() != null && !cfg.getDomain().isBlank()) b.domain(cfg.getDomain());
        return b.build();
    }

    private static void add(HttpServletResponse res, ResponseCookie c) {
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }
}
