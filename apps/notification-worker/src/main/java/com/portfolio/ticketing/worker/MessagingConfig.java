package com.portfolio.ticketing.worker;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    @Bean
    DirectExchange ticketingExchange(@Value("${app.messaging.exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange(@Value("${app.messaging.dead-letter-exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    Queue notificationQueue(
            @Value("${app.messaging.notification-queue}") String queue,
            @Value("${app.messaging.dead-letter-exchange}") String deadLetterExchange) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(queue + ".dead")
                .build();
    }

    @Bean
    Queue notificationDeadLetterQueue(@Value("${app.messaging.notification-queue}") String queue) {
        return QueueBuilder.durable(queue + ".dead").build();
    }

    @Bean
    Binding notificationBinding(
            Queue notificationQueue,
            DirectExchange ticketingExchange,
            @Value("${app.messaging.order-confirmed-routing-key}") String routingKey) {
        return BindingBuilder.bind(notificationQueue).to(ticketingExchange).with(routingKey);
    }

    @Bean
    Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            DirectExchange deadLetterExchange,
            @Value("${app.messaging.notification-queue}") String queue) {
        return BindingBuilder.bind(notificationDeadLetterQueue).to(deadLetterExchange).with(queue + ".dead");
    }
}
