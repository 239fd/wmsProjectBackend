package by.bsuir.ssoservice.config;

import by.bsuir.ssoservice.utils.JwkUtils;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
public class SecurityBeansConfig {

    @Value("${app.security.jwt.keystore-dir:./keystore}")
    private String keystoreDir;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(new JdkClientHttpRequestFactory());
    }

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate(new JdkClientHttpRequestFactory());
    }

    @Bean
    public KeyPair keyPair() {
        try {
            Path dir = Paths.get(keystoreDir);
            Path pubFile = dir.resolve("jwt-public.key");
            Path privFile = dir.resolve("jwt-private.key");
            if (Files.exists(pubFile) && Files.exists(privFile)) {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(
                        new X509EncodedKeySpec(Base64.getDecoder().decode(Files.readString(pubFile))));
                RSAPrivateKey priv = (RSAPrivateKey) kf.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(Files.readString(privFile))));
                log.info("JWT RSA keypair loaded from {}", dir.toAbsolutePath());
                return new KeyPair(pub, priv);
            }

            RSAKey rsaKey = JwkUtils.generateRsa();
            RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
            RSAPrivateKey privateKey = rsaKey.toRSAPrivateKey();
            try {
                Files.createDirectories(dir);
                Files.writeString(pubFile, Base64.getEncoder().encodeToString(publicKey.getEncoded()));
                Files.writeString(privFile, Base64.getEncoder().encodeToString(privateKey.getEncoded()));
                log.info("JWT RSA keypair generated and saved to {}", dir.toAbsolutePath());
            } catch (IOException io) {
                log.warn("Could not persist JWT keypair to {}: {} — restart will invalidate tokens",
                        dir, io.getMessage());
            }
            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load/generate RSA key pair", e);
        }
    }
}
