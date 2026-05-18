package by.bsuir.ssoservice;

import by.bsuir.ssoservice.integration.config.TestRedisConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.rabbitmq.listener.direct.auto-startup=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
        })
@ActiveProfiles("test")
@Import({TestRedisConfiguration.class, SsoServiceApplicationTests.TestRabbitConfiguration.class})
class SsoServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestRabbitConfiguration {

        @Bean
        @Primary
        public ConnectionFactory rabbitConnectionFactory() {
            return Mockito.mock(ConnectionFactory.class);
        }
    }
}
