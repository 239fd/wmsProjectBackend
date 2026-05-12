package by.bsuir.ssoservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORGANIZATION_EXCHANGE = "organization.exchange";
    public static final String WAREHOUSE_EXCHANGE = "warehouse.exchange";
    public static final String SSO_EXCHANGE = "sso.exchange";

    public static final String EMPLOYEE_STATUS_CHANGED_KEY = "employee.status.changed";
    public static final String ORGANIZATION_ARCHIVED_KEY = "organization.archived";
    public static final String WAREHOUSE_DELETED_KEY = "warehouse.deleted";
    public static final String DIRECTOR_DELETED_KEY = "user.director.deleted";

    public static final String EMPLOYEE_STATUS_CHANGED_QUEUE = "employee.status.changed.sso.queue";
    public static final String ORGANIZATION_ARCHIVED_SSO_QUEUE = "organization.archived.sso.queue";
    public static final String WAREHOUSE_DELETED_SSO_QUEUE = "warehouse.deleted.sso.queue";
    public static final String DIRECTOR_DELETED_QUEUE = "user.director.deleted.queue";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    @Bean
    public TopicExchange organizationExchange() {
        return new TopicExchange(ORGANIZATION_EXCHANGE);
    }

    @Bean
    public TopicExchange warehouseExchange() {
        return new TopicExchange(WAREHOUSE_EXCHANGE);
    }

    @Bean
    public TopicExchange ssoExchange() {
        return new TopicExchange(SSO_EXCHANGE);
    }

    @Bean
    public Queue employeeStatusChangedQueue() {
        return new Queue(EMPLOYEE_STATUS_CHANGED_QUEUE, true);
    }

    @Bean
    public Queue organizationArchivedSsoQueue() {
        return new Queue(ORGANIZATION_ARCHIVED_SSO_QUEUE, true);
    }

    @Bean
    public Queue warehouseDeletedSsoQueue() {
        return new Queue(WAREHOUSE_DELETED_SSO_QUEUE, true);
    }

    @Bean
    public Queue directorDeletedQueue() {
        return new Queue(DIRECTOR_DELETED_QUEUE, true);
    }

    @Bean
    public Binding employeeStatusChangedBinding() {
        return BindingBuilder
                .bind(employeeStatusChangedQueue())
                .to(organizationExchange())
                .with(EMPLOYEE_STATUS_CHANGED_KEY);
    }

    @Bean
    public Binding organizationArchivedSsoBinding() {
        return BindingBuilder
                .bind(organizationArchivedSsoQueue())
                .to(organizationExchange())
                .with(ORGANIZATION_ARCHIVED_KEY);
    }

    @Bean
    public Binding warehouseDeletedSsoBinding() {
        return BindingBuilder
                .bind(warehouseDeletedSsoQueue())
                .to(warehouseExchange())
                .with(WAREHOUSE_DELETED_KEY);
    }

    @Bean
    public Binding directorDeletedBinding() {
        return BindingBuilder
                .bind(directorDeletedQueue())
                .to(ssoExchange())
                .with(DIRECTOR_DELETED_KEY);
    }
}