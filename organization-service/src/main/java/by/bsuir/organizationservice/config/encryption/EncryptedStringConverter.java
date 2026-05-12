package by.bsuir.organizationservice.config.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    @Value("${app.db.encryption.key:}")
    private String encryptionKeyBase64;

    private SecretKeySpec getSecretKey() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            if (keyBytes.length != 32) {
                log.warn("APP_DB_ENCRYPTION_KEY must be 32 bytes (256-bit). Encryption disabled.");
                return null;
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            log.warn("Invalid APP_DB_ENCRYPTION_KEY. Encryption disabled: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        SecretKeySpec key = getSecretKey();
        if (key == null) {
            return attribute;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Ошибка шифрования данных", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        SecretKeySpec key = getSecretKey();
        if (key == null) {
            return dbData;
        }
        try {
            byte[] encrypted = Base64.getDecoder().decode(dbData);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Decryption failed, returning raw value (may be unencrypted legacy data)");
            return dbData;
        }
    }
}