package com.portfolio.ticketing.service;

import com.portfolio.ticketing.api.ApiException;
import com.portfolio.ticketing.api.ApiModels;
import com.portfolio.ticketing.config.AppProperties;
import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.SeatEntity;
import com.portfolio.ticketing.domain.SeatHold;
import com.portfolio.ticketing.domain.UserAccount;
import com.portfolio.ticketing.repository.SeatHoldRepository;
import com.portfolio.ticketing.repository.SeatRepository;
import com.portfolio.ticketing.repository.UserAccountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HoldService {

    private final SeatRepository seats;
    private final SeatHoldRepository holds;
    private final UserAccountRepository users;
    private final AvailabilityService availability;
    private final AppProperties.Holds properties;
    private final Clock clock;
    private final Counter attempts;
    private final Counter conflicts;
    private final Counter expirations;

    public HoldService(
            SeatRepository seats,
            SeatHoldRepository holds,
            UserAccountRepository users,
            AvailabilityService availability,
            AppProperties.Holds properties,
            Clock clock,
            MeterRegistry registry) {
        this.seats = seats;
        this.holds = holds;
        this.users = users;
        this.availability = availability;
        this.properties = properties;
        this.clock = clock;
        this.attempts = registry.counter("booking_attempt_total");
        this.conflicts = registry.counter("booking_conflict_total");
        this.expirations = registry.counter("hold_expired_total");
    }

    @Transactional
    public ApiModels.HoldResponse create(UUID seatId, UUID userId) {
        attempts.increment();
        Instant now = clock.instant();
        SeatEntity seat = seats.findByIdForUpdate(seatId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "SEAT_NOT_FOUND", "Seat was not found"));

        if (seat.getSession().getEvent().getStatus() != DomainTypes.EventStatus.PUBLISHED
                || !seat.getSession().getStartsAt().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_BOOKABLE",
                    "This event session is not open for booking");
        }

        if (seat.getStatus() == DomainTypes.SeatStatus.HELD) {
            SeatHold existing = holds.findBySeatIdAndStatus(seatId, DomainTypes.HoldStatus.ACTIVE).orElse(null);
            if (existing != null && existing.isExpired(now)) {
                existing.expire();
                seat.release();
                holds.flush();
                expirations.increment();
            }
        }

        if (seat.getStatus() != DomainTypes.SeatStatus.AVAILABLE) {
            conflicts.increment();
            throw new ApiException(HttpStatus.CONFLICT, "SEAT_UNAVAILABLE", "Seat is no longer available");
        }

        UserAccount user = users.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Authenticated user no longer exists"));
        seat.hold();
        SeatHold hold = holds.save(new SeatHold(seat, user, now.plus(properties.ttl())));
        availability.invalidate(seat.getSession().getId());
        return response(hold, now);
    }

    @Transactional(readOnly = true)
    public ApiModels.HoldResponse get(UUID holdId, UUID userId) {
        SeatHold hold = holds.findById(holdId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "Seat hold was not found"));
        requireOwner(hold, userId);
        return response(hold, clock.instant());
    }

    @Transactional
    public ApiModels.HoldResponse release(UUID holdId, UUID userId) {
        SeatHold hold = holds.findByIdForUpdate(holdId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "Seat hold was not found"));
        requireOwner(hold, userId);
        if (hold.getStatus() == DomainTypes.HoldStatus.ACTIVE) {
            hold.release();
            hold.getSeat().release();
            availability.invalidate(hold.getSeat().getSession().getId());
        }
        return response(hold, clock.instant());
    }

    @Transactional
    public int expireBatch() {
        Instant now = clock.instant();
        List<SeatHold> expired = holds.findExpiredForUpdate(now, properties.expiryBatchSize());
        for (SeatHold hold : expired) {
            hold.expire();
            hold.getSeat().release();
            availability.invalidate(hold.getSeat().getSession().getId());
            expirations.increment();
        }
        return expired.size();
    }

    private void requireOwner(SeatHold hold, UUID userId) {
        if (!hold.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HOLD_ACCESS_DENIED", "You do not own this seat hold");
        }
    }

    private ApiModels.HoldResponse response(SeatHold hold, Instant now) {
        long remaining = hold.getStatus() == DomainTypes.HoldStatus.ACTIVE
                ? Math.max(Duration.between(now, hold.getExpiresAt()).toSeconds(), 0)
                : 0;
        return new ApiModels.HoldResponse(
                hold.getId(),
                hold.getSeat().getId(),
                hold.getUser().getId(),
                hold.getStatus(),
                hold.getExpiresAt(),
                remaining);
    }
}
