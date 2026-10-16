package app.harmonis.integrations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher("chave-de-teste-com-tamanho-suficiente");

    @Test
    void cifra_e_decifra_ida_e_volta() {
        String enc = cipher.encrypt("s3nh@-çãõ");
        assertNotEquals("s3nh@-çãõ", enc);
        assertTrue(enc.startsWith("enc:v1:"));
        assertEquals("s3nh@-çãõ", cipher.decrypt(enc));
    }

    @Test
    void mesmo_texto_gera_cifras_diferentes_iv_aleatorio() {
        assertNotEquals(cipher.encrypt("abc"), cipher.encrypt("abc"));
    }

    @Test
    void nao_cifra_duas_vezes_e_trata_vazio() {
        String enc = cipher.encrypt("x");
        assertEquals(enc, cipher.encrypt(enc));
        assertNull(cipher.encrypt(null));
        assertNull(cipher.encrypt(""));
        assertNull(cipher.decrypt(null));
    }

    @Test
    void chave_diferente_nao_decifra() {
        String enc = cipher.encrypt("segredo");
        SecretCipher other = new SecretCipher("outra-chave-totalmente-diferente-123");
        assertThrows(IllegalStateException.class, () -> other.decrypt(enc));
    }

    @Test
    void adulteracao_do_texto_cifrado_e_detectada() {
        String enc = cipher.encrypt("segredo");
        String tampered = enc.substring(0, enc.length() - 4) + (enc.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }
}
