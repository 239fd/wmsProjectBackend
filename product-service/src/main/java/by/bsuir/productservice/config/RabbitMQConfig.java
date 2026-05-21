package by.bsuir.productservice.config;

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

    public static final String PRODUCT_EXCHANGE = "product.exchange";
    public static final String ORGANIZATION_EXCHANGE = "organization.exchange";

    public static final String PRODUCT_RECEIVED_QUEUE = "product.received.queue";
    public static final String PRODUCT_SHIPPED_QUEUE = "product.shipped.queue";
    public static final String PRODUCT_WRITTEN_OFF_QUEUE = "product.written_off.queue";
    public static final String PRODUCT_REVALUATED_QUEUE = "product.revaluated.queue";
    public static final String ORGANIZATION_DELETED_PRODUCT_QUEUE = "organization.deleted.product.queue";

    public static final String PRODUCT_RECEIVED_KEY = "product.received";
    public static final String PRODUCT_SHIPPED_KEY = "product.shipped";
    public static final String PRODUCT_WRITTEN_OFF_KEY = "product.written_off";
    public static final String PRODUCT_REVALUATED_KEY = "product.revaluated";
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
    public TopicExchange productExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE);
    }

    @Bean
    public TopicExchange organizationExchange() {
        return new TopicExchange(ORGANIZATION_EXCHANGE);
    }

    @Bean
    public Queue organizationDeletedProductQueue() {
        return new Queue(ORGANIZATION_DELETED_PRODUCT_QUEUE, true);
    }

    @Bean
    public Binding organizationDeletedProductBinding() {
        return BindingBuilder.bind(organizationDeletedProductQueue()).to(organizationExchange()).with(ORGANIZATION_DELETED_KEY);
    }

    @Bean
    public Queue productReceivedQueue() {
        return new Queue(PRODUCT_RECEIVED_QUEUE, true);
    }

    @Bean
    public Queue productShippedQueue() {
        return new Queue(PRODUCT_SHIPPED_QUEUE, true);
    }

    @Bean
    public Queue productWrittenOffQueue() {
        return new Queue(PRODUCT_WRITTEN_OFF_QUEUE, true);
    }

    @Bean
    public Queue productRevaluatedQueue() {
        return new Queue(PRODUCT_REVALUATED_QUEUE, true);
    }

    @Bean
    public Binding productReceivedBinding() {
        return BindingBuilder.bind(productReceivedQueue()).to(productExchange()).with(PRODUCT_RECEIVED_KEY);
    }

    @Bean
    public Binding productShippedBinding() {
        return BindingBuilder.bind(productShippedQueue()).to(productExchange()).with(PRODUCT_SHIPPED_KEY);
    }

    @Bean
    public Binding productWrittenOffBinding() {
        return BindingBuilder.bind(productWrittenOffQueue()).to(productExchange()).with(PRODUCT_WRITTEN_OFF_KEY);
    }

    @Bean
    public Binding productRevaluatedBinding() {
        return BindingBuilder.bind(productRevaluatedQueue()).to(productExchange()).with(PRODUCT_REVALUATED_KEY);
    }
}