package app.harmonis.integrations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Protecao contra SSRF para URLs de webhook (e de pre-autenticacao).
 * Sempre bloqueia: esquemas fora de http/https, credenciais embutidas na URL, enderecos
 * "any/multicast" e link-local (inclui o endpoint de metadados de nuvem 169.254.169.254).
 * Salvo {@code harmonis.webhooks.allow-private-networks=true}, bloqueia tambem loopback e
 * redes privadas (10/8, 172.16/12, 192.168/16, 100.64/10, fc00::/7).
 */
@Component
public class UrlGuard {

    private final boolean allowPrivate;

    public UrlGuard(@Value("${harmonis.webhooks.allow-private-networks:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    /** Validacao estatica (no cadastro): sintaxe, esquema e host literal. Nao resolve DNS. */
    public String validateSyntax(String raw) {
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (Exception e) {
            return "URL inválida.";
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return "A URL deve começar com http:// ou https://.";
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) return "URL sem host.";
        if (uri.getUserInfo() != null) {
            return "Não informe usuário/senha na URL; use a configuração de autenticação.";
        }
        if (uri.getFragment() != null) return "A URL não pode conter fragmento (#).";
        if (isIpLiteral(uri.getHost())) {
            try {
                String blocked = blockReason(InetAddress.getByName(uri.getHost()));
                if (blocked != null) return blocked;
            } catch (UnknownHostException ignored) {
                // literal ja validado por isIpLiteral
            }
        }
        return null;
    }

    /** Validacao no envio: sintaxe + resolucao DNS (todos os enderecos devem ser permitidos). */
    public String validateForSend(URI uri) {
        String syntax = validateSyntax(uri.toString());
        if (syntax != null) return syntax;
        try {
            for (InetAddress addr : InetAddress.getAllByName(uri.getHost())) {
                String blocked = blockReason(addr);
                if (blocked != null) return blocked;
            }
        } catch (UnknownHostException e) {
            return "Não foi possível resolver o host: " + uri.getHost();
        }
        return null;
    }

    String blockReason(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isMulticastAddress()) {
            return "Endereço de destino não permitido.";
        }
        if (a.isLinkLocalAddress()) {
            return "Endereços link-local (ex.: metadados de nuvem) são bloqueados.";
        }
        if (!allowPrivate && isPrivate(a)) {
            return "Destino em rede privada/loopback bloqueado (harmonis.webhooks.allow-private-networks=false).";
        }
        return null;
    }

    private static boolean isPrivate(InetAddress a) {
        if (a.isLoopbackAddress() || a.isSiteLocalAddress()) return true;
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) == 0xFC; // fc00::/7 (unique local)
        }
        int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
        if (b0 == 0) return true;                           // 0.0.0.0/8
        return b0 == 100 && b1 >= 64 && b1 <= 127;          // 100.64.0.0/10 (CGNAT)
    }

    private static boolean isIpLiteral(String host) {
        if (host.startsWith("[")) return true; // IPv6 entre colchetes
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }
}
