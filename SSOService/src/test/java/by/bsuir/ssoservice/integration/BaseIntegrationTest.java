package by.bsuir.ssoservice.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Базовый класс для интеграционных тестов SSOService.
 *
 * Использует:
 * - H2 in-memory БД вместо PostgreSQL
 * - MockMvc для тестирования REST API
 * - Отключенный Eureka client
 * - Тестовый профиль конфигурации
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
public abstract class BaseIntegrationTest {

    // Базовый класс для наследования интеграционных тестов
    // Содержит общую конфигурацию Spring Boot Test
}

