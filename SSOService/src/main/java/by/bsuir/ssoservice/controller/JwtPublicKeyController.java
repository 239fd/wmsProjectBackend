package by.bsuir.ssoservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "JWT публичный ключ", description = "API для получения публичного ключа для проверки JWT токенов")
public class JwtPublicKeyController {

    private final KeyPair keyPair;

    @Operation(
            summary = "Получить публичный ключ JWT",
            description = "Возвращает публичный ключ RSA для проверки подписи JWT токенов. Используется другими микросервисами для валидации токенов."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Публичный ключ успешно получен"),
            @ApiResponse(responseCode = "500", description = "Ошибка получения ключа")
    })
    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            String publicKeyPEM = convertToPEM(publicKey);

            log.debug("Public key requested");

            return ResponseEntity.ok(Map.of(
                    "publicKey", publicKeyPEM,
                    "algorithm", "RS256",
                    "keyId", "sso-key-id"
            ));
        } catch (Exception e) {
            log.error("Error getting public key", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String convertToPEM(RSAPublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        String base64Encoded = Base64.getEncoder().encodeToString(encoded);

        return "-----BEGIN PUBLIC KEY-----\n" +
                base64Encoded +
                "\n-----END PUBLIC KEY-----";
    }
}
