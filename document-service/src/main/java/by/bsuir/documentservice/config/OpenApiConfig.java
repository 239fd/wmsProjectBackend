package by.bsuir.documentservice.config;

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

    @Value("${server.port:8040}")
    private String serverPort;

    @Bean
    public OpenAPI documentServiceAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:" + serverPort);
        localServer.setDescription("Локальный сервер");

        Server gatewayServer = new Server();
        gatewayServer.setUrl("http://localhost:8765/document-service");
        gatewayServer.setDescription("Через API Gateway");

        License license = new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html");

        Info info = new Info()
                .title("Document Service API")
                .version("1.0.0")
                .description("REST API для генерации документов в системе WMS с использованием RPA (Robotic Process Automation). " +
                        "Поддерживает генерацию накладных, актов переоценки, инвентаризационных описей и других складских документов.")
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer, gatewayServer));
    }
}

