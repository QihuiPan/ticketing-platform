package com.portfolio.ticketing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

public final class AppProperties {

    private AppProperties() {
    }

    @ConfigurationProperties("app.auth")
    public record Auth(String issuer, Duration accessTokenTtl, String jwtSecret) {
    }

    @ConfigurationProperties("app.holds")
    public record Holds(Duration ttl, int expiryBatchSize) {
    }

    @ConfigurationProperties("app.messaging")
    public record Messaging(
            String exchange,
            String orderConfirmedRoutingKey,
            String notificationQueue,
            String deadLetterExchange) {
    }
}
