package by.bsuir.organizationservice.config;

import org.springframework.amqp.core.*;
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

    public static final String ORGANIZATION_CREATED_QUEUE = "organization.created.queue";
    public static final String ORGANIZATION_UPDATED_QUEUE = "organization.updated.queue";
    public static final String ORGANIZATION_DELETED_QUEUE = "organization.deleted.queue";
    public static final String WAREHOUSE_INFO_REQUEST_QUEUE = "warehouse.info.request.queue";
    public static final String WAREHOUSE_INFO_RESPONSE_QUEUE = "warehouse.info.response.queue";

    public static final String ORGANIZATION_CREATED_KEY = "organization.created";
    public static final String ORGANIZATION_UPDATED_KEY = "organization.updated";
    public static final String ORGANIZATION_DELETED_KEY = "organization.deleted";
    public static final String WAREHOUSE_INFO_REQUEST_KEY = "warehouse.info.request";
    public static final String WAREHOUSE_INFO_RESPONSE_KEY = "warehouse.info.response";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());

        rabbitTemplate.setReplyTimeout(5000);
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
    public Queue organizationCreatedQueue() {
        return new Queue(ORGANIZATION_CREATED_QUEUE, true);
    }

    @Bean
    public Queue organizationUpdatedQueue() {
        return new Queue(ORGANIZATION_UPDATED_QUEUE, true);
    }

    @Bean
    public Queue organizationDeletedQueue() {
        return new Queue(ORGANIZATION_DELETED_QUEUE, true);
    }

    @Bean
    public Queue warehouseInfoRequestQueue() {
        return new Queue(WAREHOUSE_INFO_REQUEST_QUEUE, true);
    }

    @Bean
    public Queue warehouseInfoResponseQueue() {
        return new Queue(WAREHOUSE_INFO_RESPONSE_QUEUE, true);
    }

    @Bean
    public Binding organizationCreatedBinding() {
        return BindingBuilder
                .bind(organizationCreatedQueue())
                .to(organizationExchange())
                .with(ORGANIZATION_CREATED_KEY);
    }

    @Bean
    public Binding organizationUpdatedBinding() {
        return BindingBuilder
                .bind(organizationUpdatedQueue())
                .to(organizationExchange())
                .with(ORGANIZATION_UPDATED_KEY);
    }

    @Bean
    public Binding organizationDeletedBinding() {
        return BindingBuilder
                .bind(organizationDeletedQueue())
                .to(organizationExchange())
                .with(ORGANIZATION_DELETED_KEY);
    }

    @Bean
    public Binding warehouseInfoRequestBinding() {
        return BindingBuilder
                .bind(warehouseInfoRequestQueue())
                .to(warehouseExchange())
                .with(WAREHOUSE_INFO_REQUEST_KEY);
    }

    @Bean
    public Binding warehouseInfoResponseBinding() {
        return BindingBuilder
                .bind(warehouseInfoResponseQueue())
                .to(warehouseExchange())
                .with(WAREHOUSE_INFO_RESPONSE_KEY);
    }
}
