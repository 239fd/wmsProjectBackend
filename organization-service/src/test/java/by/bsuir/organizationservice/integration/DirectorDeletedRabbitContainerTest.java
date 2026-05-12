package by.bsuir.organizationservice.integration;

import by.bsuir.organizationservice.config.RabbitMQConfig;
import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.repository.OrganizationReadModelRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("rabbit-it")
@DisplayName("Cross-service Rabbit: user.director.deleted → организация архивирована")
class DirectorDeletedRabbitContainerTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("organization_db_rabbit")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @SuppressWarnings("resource")
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withReuse(true);

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");

        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired OrganizationReadModelRepository readModelRepository;

    @Test
    @DisplayName("Publish user.director.deleted → listener архивирует организацию")
    void publishDirectorDeleted_ShouldTriggerListenerAndArchiveOrg() {
        UUID orgId = UUID.randomUUID();
        UUID directorUserId = UUID.randomUUID();
        OrganizationReadModel org = OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ТЕСТ ОАО")
                .shortName("Тест")
                .unp("123456789")
                .address("Минск")
                .status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        readModelRepository.save(org);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SSO_EXCHANGE,
                RabbitMQConfig.DIRECTOR_DELETED_KEY,
                Map.of(
                        "userId", directorUserId.toString(),
                        "orgId", orgId.toString()
                )
        );

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    var reloaded = readModelRepository.findByOrgId(orgId).orElseThrow();
                    assertThat(reloaded.getStatus()).isEqualTo(OrganizationStatus.ARCHIVED);
                });
    }
}
