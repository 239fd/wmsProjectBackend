package by.bsuir.ssoservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtPublicKeyControllerTest {

    @Mock
    private KeyPair keyPair;

    @Mock
    private java.security.interfaces.RSAPublicKey publicKey;

    @InjectMocks
    private JwtPublicKeyController jwtPublicKeyController;

    @BeforeEach
    void setUp() throws Exception {
        when(keyPair.getPublic()).thenReturn(publicKey);
        when(publicKey.getEncoded()).thenReturn(generatePublicKeyBytes());
    }

    @Test
    void getPublicKey_ShouldReturnPublicKeyInfo() {
        ResponseEntity<Map<String, String>> response = jwtPublicKeyController.getPublicKey();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("publicKey", "algorithm", "keyId");
        assertThat(response.getBody().get("algorithm")).isEqualTo("RS256");
        assertThat(response.getBody().get("keyId")).isEqualTo("sso-key-id");
    }

    private byte[] generatePublicKeyBytes() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair().getPublic().getEncoded();
    }
}

