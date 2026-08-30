package com.portfolio.ticketing.service;

import com.portfolio.ticketing.config.AppProperties;
import com.portfolio.ticketing.domain.OutboxEvent;
import com.portfolio.ticketing.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository events;
    private final RabbitTemplate rabbit;
    private final AppProperties.Messaging properties;
    private final Clock clock;
    private final AtomicLong lagSeconds = new AtomicLong();

    public OutboxPublisher(
            OutboxEventRepository events,
            RabbitTemplate rabbit,
            AppProperties.Messaging properties,
            Clock clock,
            io.micrometer.core.instrument.MeterRegistry registry) {
        this.events = events;
        this.rabbit = rabbit;
        this.properties = properties;
        this.clock = clock;
        Gauge.builder("outbox_lag_seconds", lagSeconds, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${app.messaging.publisher-interval:PT1S}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = events.findUnpublishedForUpdate(100);
        lagSeconds.set(batch.isEmpty()
                ? 0
                : Math.max(Duration.between(batch.getFirst().getCreatedAt(), clock.instant()).toSeconds(), 0));
        for (OutboxEvent event : batch) {
            event.recordAttempt();
            try {
                CorrelationData correlation = new CorrelationData(event.getId().toString());
                rabbit.convertAndSend(
                        properties.exchange(),
                        properties.orderConfirmedRoutingKey(),
                        event.getPayload(),
                        message -> {
                            message.getMessageProperties().setMessageId(event.getId().toString());
                            message.getMessageProperties().setContentType("application/json");
                            return message;
                        },
                        correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) {
                    throw new IllegalStateException("RabbitMQ did not confirm the outbox event");
                }
                event.markPublished();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Outbox publishing was interrupted for event {}", event.getId());
                break;
            } catch (Exception publishFailure) {
                LOGGER.warn("Outbox publish failed for event {} on attempt {}", event.getId(), event.getAttempts());
                break;
            }
        }
    }
}
