package by.bsuir.warehouseservice.config;

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

    @Value("${server.port:8020}")
    private String serverPort;

    @Bean
    public OpenAPI warehouseServiceAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:" + serverPort);
        localServer.setDescription("Локальный сервер");

        Server gatewayServer = new Server();
        gatewayServer.setUrl("http://localhost:8765/warehouse-service");
        gatewayServer.setDescription("Через API Gateway");

        Contact contact = new Contact();
        contact.setName("WMS Team");
        contact.setEmail("wms-support@bsuir.by");

        License license = new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html");

        Info info = new Info()
                .title("Warehouse Service API")
                .version("1.0.0")
                .description("REST API для управления складами, стеллажами, зонами хранения и аналитикой в системе WMS. " +
                        "Поддерживает различные типы стеллажей: полочные, ячеечные, паллетные и холодильные камеры.")
                .contact(contact)
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer, gatewayServer));
    }
}

