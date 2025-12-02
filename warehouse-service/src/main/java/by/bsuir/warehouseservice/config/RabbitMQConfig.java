package by.bsuir.warehouseservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {


    public static final String WAREHOUSE_EXCHANGE = "warehouse.exchange";
    public static final String ORGANIZATION_EXCHANGE = "organization.exchange";


    public static final String WAREHOUSE_CREATED_QUEUE = "warehouse.created.queue";
    public static final String WAREHOUSE_UPDATED_QUEUE = "warehouse.updated.queue";
    public static final String WAREHOUSE_DELETED_QUEUE = "warehouse.deleted.queue";
    public static final String WAREHOUSE_INFO_REQUEST_QUEUE = "warehouse.info.request.queue";
    public static final String WAREHOUSE_INFO_RESPONSE_QUEUE = "warehouse.info.response.queue";
    public static final String ORGANIZATION_DELETED_QUEUE = "organization.deleted.warehouse.queue";


    public static final String WAREHOUSE_CREATED_KEY = "warehouse.created";
    public static final String WAREHOUSE_UPDATED_KEY = "warehouse.updated";
    public static final String WAREHOUSE_DELETED_KEY = "warehouse.deleted";
    public static final String WAREHOUSE_INFO_REQUEST_KEY = "warehouse.info.request";
    public static final String WAREHOUSE_INFO_RESPONSE_KEY = "warehouse.info.response";
    public static final String ORGANIZATION_DELETED_KEY = "organization.deleted";

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
    public TopicExchange warehouseExchange() {
        return new TopicExchange(WAREHOUSE_EXCHANGE);
    }

    @Bean
    public TopicExchange organizationExchange() {
        return new TopicExchange(ORGANIZATION_EXCHANGE);
    }


    @Bean
    public Queue warehouseCreatedQueue() {
        return new Queue(WAREHOUSE_CREATED_QUEUE, true);
    }

    @Bean
    public Queue warehouseUpdatedQueue() {
        return new Queue(WAREHOUSE_UPDATED_QUEUE, true);
    }

    @Bean
    public Queue warehouseDeletedQueue() {
        return new Queue(WAREHOUSE_DELETED_QUEUE, true);
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
    public Queue organizationDeletedWarehouseQueue() {
        return new Queue(ORGANIZATION_DELETED_QUEUE, true);
    }


    @Bean
    public Binding warehouseCreatedBinding() {
        return BindingBuilder
                .bind(warehouseCreatedQueue())
                .to(warehouseExchange())
                .with(WAREHOUSE_CREATED_KEY);
    }

    @Bean
    public Binding warehouseUpdatedBinding() {
        return BindingBuilder
                .bind(warehouseUpdatedQueue())
                .to(warehouseExchange())
                .with(WAREHOUSE_UPDATED_KEY);
    }

    @Bean
    public Binding warehouseDeletedBinding() {
        return BindingBuilder
                .bind(warehouseDeletedQueue())
                .to(warehouseExchange())
                .with(WAREHOUSE_DELETED_KEY);
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

    @Bean
    public Binding organizationDeletedWarehouseBinding() {
        return BindingBuilder
                .bind(organizationDeletedWarehouseQueue())
                .to(organizationExchange())
                .with(ORGANIZATION_DELETED_KEY);
    }
}
