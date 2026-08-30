package com.portfolio.ticketing.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    DirectExchange ticketingExchange(AppProperties.Messaging properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    DirectExchange deadLetterExchange(AppProperties.Messaging properties) {
        return new DirectExchange(properties.deadLetterExchange(), true, false);
    }

    @Bean
    Queue notificationQueue(AppProperties.Messaging properties) {
        return QueueBuilder.durable(properties.notificationQueue())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(properties.notificationQueue() + ".dead")
                .build();
    }

    @Bean
    Queue notificationDeadLetterQueue(AppProperties.Messaging properties) {
        return QueueBuilder.durable(properties.notificationQueue() + ".dead").build();
    }

    @Bean
    Binding notificationBinding(
            Queue notificationQueue,
            DirectExchange ticketingExchange,
            AppProperties.Messaging properties) {
        return BindingBuilder.bind(notificationQueue)
                .to(ticketingExchange)
                .with(properties.orderConfirmedRoutingKey());
    }

    @Bean
    Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            DirectExchange deadLetterExchange,
            AppProperties.Messaging properties) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.notificationQueue() + ".dead");
    }
}
