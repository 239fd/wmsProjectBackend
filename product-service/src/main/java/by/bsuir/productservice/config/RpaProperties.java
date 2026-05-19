package by.bsuir.productservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rpa")
@Getter
@Setter
public class RpaProperties {

    private final Python python = new Python();

    @Getter
    @Setter
    public static class Python {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8060";
        private int timeoutSeconds = 300;
    }
}