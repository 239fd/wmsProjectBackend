package by.bsuir.ssoservice.utils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwkUtils Unit Tests")
class JwkUtilsTest {

    @Test
    @DisplayName("generateRsa: Should generate valid RSA key with keyID")
    void generateRsa_ShouldGenerateValidRsaKeyWithKeyId() throws JOSEException {
        RSAKey rsaKey = JwkUtils.generateRsa();

        assertThat(rsaKey).isNotNull();
        assertThat(rsaKey.getKeyID()).isNotNull();
        assertThat(rsaKey.getKeyID()).isNotEmpty();
        assertThat(rsaKey.toRSAPublicKey()).isNotNull();
        assertThat(rsaKey.toRSAPrivateKey()).isNotNull();
    }

    @Test
    @DisplayName("generateRsa: Should generate different keys on each call")
    void generateRsa_ShouldGenerateDifferentKeysOnEachCall() throws JOSEException {
        RSAKey rsaKey1 = JwkUtils.generateRsa();
        RSAKey rsaKey2 = JwkUtils.generateRsa();

        assertThat(rsaKey1.getKeyID()).isNotEqualTo(rsaKey2.getKeyID());
        assertThat(rsaKey1.toRSAPublicKey()).isNotEqualTo(rsaKey2.toRSAPublicKey());
    }

    @Test
    @DisplayName("generateRsaKey: Should generate valid KeyPair")
    void generateRsaKey_ShouldGenerateValidKeyPair() {
        KeyPair keyPair = JwkUtils.generateRsaKey();

        assertThat(keyPair).isNotNull();
        assertThat(keyPair.getPublic()).isInstanceOf(RSAPublicKey.class);
        assertThat(keyPair.getPrivate()).isInstanceOf(RSAPrivateKey.class);
    }

    @Test
    @DisplayName("generateRsaKey: Should generate 2048-bit keys")
    void generateRsaKey_ShouldGenerate2048BitKeys() {
        KeyPair keyPair = JwkUtils.generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        assertThat(publicKey.getModulus().bitLength()).isEqualTo(2048);
    }

    @Test
    @DisplayName("generateRsaKey: Should generate different keys on each call")
    void generateRsaKey_ShouldGenerateDifferentKeysOnEachCall() {
        KeyPair keyPair1 = JwkUtils.generateRsaKey();
        KeyPair keyPair2 = JwkUtils.generateRsaKey();

        assertThat(keyPair1.getPublic()).isNotEqualTo(keyPair2.getPublic());
        assertThat(keyPair1.getPrivate()).isNotEqualTo(keyPair2.getPrivate());
    }
}

