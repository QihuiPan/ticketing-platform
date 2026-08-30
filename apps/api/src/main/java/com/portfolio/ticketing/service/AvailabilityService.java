package com.portfolio.ticketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.ticketing.api.ApiException;
import com.portfolio.ticketing.api.ApiModels;
import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.EventSessionEntity;
import com.portfolio.ticketing.repository.EventSessionRepository;
import com.portfolio.ticketing.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
public class AvailabilityService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final EventSessionRepository sessions;
    private final SeatRepository seats;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter cacheFallbackCounter;

    public AvailabilityService(
            EventSessionRepository sessions,
            SeatRepository seats,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            Clock clock,
            MeterRegistry registry) {
        this.sessions = sessions;
        this.seats = seats;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.cacheFallbackCounter = registry.counter("availability_cache_fallback_total");
    }

    @Transactional(readOnly = true)
    public ApiModels.AvailabilityResponse get(UUID sessionId) {
        String key = cacheKey(sessionId);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, ApiModels.AvailabilityResponse.class);
            }
        } catch (RuntimeException | JsonProcessingException unavailable) {
            cacheFallbackCounter.increment();
        }

        EventSessionEntity session = sessions.findById(sessionId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Event session was not found"));
        if (session.getEvent().getStatus() != DomainTypes.EventStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Event session was not found");
        }
        ApiModels.AvailabilityResponse response = new ApiModels.AvailabilityResponse(
                sessionId,
                clock.instant(),
                seats.findBySessionIdOrderByLabel(sessionId).stream()
                        .map(seat -> new ApiModels.SeatAvailability(
                                seat.getId(), seat.getLabel(), seat.getPrice(), seat.getStatus()))
                        .toList());
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (RuntimeException | JsonProcessingException unavailable) {
            cacheFallbackCounter.increment();
        }
        return response;
    }

    public void invalidate(UUID sessionId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(sessionId);
                }
            });
            return;
        }
        delete(sessionId);
    }

    private void delete(UUID sessionId) {
        try {
            redis.delete(cacheKey(sessionId));
        } catch (RuntimeException unavailable) {
            cacheFallbackCounter.increment();
        }
    }

    private String cacheKey(UUID sessionId) {
        return "availability:" + sessionId;
    }
}
