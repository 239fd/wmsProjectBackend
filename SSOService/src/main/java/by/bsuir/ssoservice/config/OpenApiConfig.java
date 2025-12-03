package by.bsuir.ssoservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8000}")
    private String serverPort;

    @Bean
    public OpenAPI ssoServiceAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:" + serverPort);
        localServer.setDescription("Локальный сервер");

        Server gatewayServer = new Server();
        gatewayServer.setUrl("http://localhost:8765/sso-service");
        gatewayServer.setDescription("Через API Gateway");

        License license = new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html");

        Info info = new Info()
                .title("SSO Service API")
                .version("1.0.0")
                .description("REST API для аутентификации и авторизации пользователей в системе WMS. " +
                        "Поддерживает регистрацию, вход, OAuth2 (Google, Yandex), управление профилями и JWT токенами.")
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer, gatewayServer));
    }
}

