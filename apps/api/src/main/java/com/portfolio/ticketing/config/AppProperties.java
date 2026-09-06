package com.portfolio.ticketing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

public final class AppProperties {

    private AppProperties() {
    }

    @ConfigurationProperties("app.auth")
    public record Auth(String issuer, Duration accessTokenTtl, String jwtSecret) {
    }

    @ConfigurationProperties("app.holds")
    public record Holds(Duration ttl, int expiryBatchSize) {
    }

    @ConfigurationProperties("app.cors")
    public record Cors(List<String> allowedOriginPatterns) {
    }

    @ConfigurationProperties("app.messaging")
    public record Messaging(
            String exchange,
            String orderConfirmedRoutingKey,
            String notificationQueue,
            String deadLetterExchange) {
    }
}
