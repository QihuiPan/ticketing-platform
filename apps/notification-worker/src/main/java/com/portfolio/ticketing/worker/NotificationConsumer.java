package com.portfolio.ticketing.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
public class NotificationConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationConsumer.class);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final Counter delivered;
    private final Counter duplicates;

    public NotificationConsumer(ObjectMapper objectMapper, JdbcTemplate jdbc, MeterRegistry registry) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.delivered = registry.counter("notification_delivered_total");
        this.duplicates = registry.counter("notification_duplicate_total");
    }

    @RabbitListener(queues = "${app.messaging.notification-queue}")
    @Transactional
    public void consume(String body) throws JsonProcessingException {
        JsonNode event = objectMapper.readTree(body);
        UUID eventId = requiredUuid(event, "eventId");
        UUID orderId = requiredUuid(event, "orderId");
        String recipient = requiredText(event, "recipient");

        int inserted = jdbc.update("""
                INSERT INTO notification_deliveries(event_id, order_id, recipient, status, delivered_at)
                VALUES (?, ?, ?, 'DELIVERED', ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, orderId, recipient, Timestamp.from(Instant.now()));
        if (inserted == 0) {
            duplicates.increment();
            LOGGER.info("Ignored duplicate order confirmation event {}", eventId);
            return;
        }

        // This portfolio worker records a deterministic delivery instead of calling a real email provider.
        delivered.increment();
        LOGGER.info("Delivered ticket notification for order {} to recipient {}", orderId, maskEmail(recipient));
    }

    private UUID requiredUuid(JsonNode event, String field) {
        return UUID.fromString(requiredText(event, field));
    }

    private String requiredText(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalArgumentException("Message field is required: " + field);
        }
        return value.asText();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
