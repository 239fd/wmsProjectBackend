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

    private final Onec onec = new Onec();

    @Getter
    @Setter
    public static class Onec {
        private boolean enabled;
        private String driverUrl = "http://127.0.0.1:4723";
        private String executable = "C:\\Program Files\\1cv8\\common\\1cestart.exe";
        private String basePath = "";
        private String username = "";
        private String password = "";
        private String sectionName = "Закупки";
        private String journalName = "Заказы поставщикам";
        private int waitSeconds = 30;
        private String windowTitlePattern = "1С";
        private boolean attachMode = true;
    }
}
