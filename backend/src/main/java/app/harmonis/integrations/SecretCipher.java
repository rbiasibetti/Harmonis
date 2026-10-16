package app.harmonis.integrations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra segredos de integracao (senhas, tokens, chaves) em repouso com AES-256-GCM.
 * A chave vem de {@code harmonis.secrets.key} (SHA-256 do valor configurado). O texto
 * cifrado leva o prefixo {@code enc:v1:} + base64(iv[12] + ciphertext + tag).
 */
@Component
public class SecretCipher {

    static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${harmonis.secrets.key}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("harmonis.secrets.key nao configurada.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** Cifra o texto; nulo/vazio retorna nulo. Valores ja cifrados nao sao cifrados de novo. */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        if (isEncrypted(plain)) return plain;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar segredo.", e);
        }
    }

    /** Decifra; nulo/vazio retorna nulo. Valor sem prefixo e devolvido como esta (legado). */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return null;
        if (!isEncrypted(stored)) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Nao foi possivel decifrar o segredo (chave harmonis.secrets.key alterada?).", e);
        }
    }
}
