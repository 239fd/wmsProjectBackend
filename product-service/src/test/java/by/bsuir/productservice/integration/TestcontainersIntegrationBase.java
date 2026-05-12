package by.bsuir.productservice.integration;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.mockito.Mockito.mock;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@EnabledIf(
        value = "by.bsuir.productservice.integration.DockerAvailability#dockerAvailable",
        disabledReason = "Docker daemon недоступен — интеграционный тест пропущен (запустите Docker Desktop)."
)
public abstract class TestcontainersIntegrationBase {

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (DockerAvailability.dockerAvailable()) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("product_db_it")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        if (POSTGRES == null) return;
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @TestConfiguration
    static class RabbitMocks {
        @Bean
        @Primary
        public ConnectionFactory testConnectionFactory() {
            return mock(CachingConnectionFactory.class);
        }

        @Bean
        @Primary
        public RabbitTemplate testRabbitTemplate() {
            return mock(RabbitTemplate.class);
        }
    }
}
