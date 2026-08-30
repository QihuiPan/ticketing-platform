package com.portfolio.ticketing.api;

import com.portfolio.ticketing.domain.DomainTypes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiModels {

    private ApiModels() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 10, max = 100) String password) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, Instant expiresAt, UserResponse user) {
    }

    public record UserResponse(UUID id, String email, DomainTypes.Role role) {
    }

    public record CreateEventRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull @Size(max = 2000) String description) {
    }

    public record EventResponse(
            UUID id,
            String title,
            String description,
            DomainTypes.EventStatus status,
            Instant createdAt) {
    }

    public record CreateSessionRequest(
            @NotNull @Future Instant startsAt,
            @NotBlank @Size(max = 200) String venue,
            @NotEmpty @Size(max = 1000) List<@Valid SeatDefinition> seats) {
    }

    public record SeatDefinition(
            @NotBlank @Size(max = 64) String label,
            @NotNull @DecimalMin("0.00") BigDecimal price) {
    }

    public record SessionResponse(UUID id, UUID eventId, Instant startsAt, String venue) {
    }

    public record SeatAvailability(
            UUID id,
            String label,
            BigDecimal price,
            DomainTypes.SeatStatus status) {
    }

    public record AvailabilityResponse(UUID sessionId, Instant serverTime, List<SeatAvailability> seats) {
    }

    public record CreateHoldRequest(@NotNull UUID seatId) {
    }

    public record HoldResponse(
            UUID id,
            UUID seatId,
            UUID userId,
            DomainTypes.HoldStatus status,
            Instant expiresAt,
            long remainingSeconds) {
    }

    public record CreateOrderRequest(@NotNull UUID holdId) {
    }

    public record OrderResponse(
            UUID id,
            UUID holdId,
            BigDecimal amount,
            DomainTypes.OrderStatus status,
            String ticketCode,
            Instant createdAt) {
    }

    public record PaymentRequest(@NotNull UUID orderId) {
    }

    public record PaymentResponse(
            UUID id,
            UUID orderId,
            BigDecimal amount,
            DomainTypes.PaymentStatus status,
            Instant capturedAt,
            Instant refundedAt) {
    }

    public record ErrorResponse(String code, String message, Instant timestamp) {
    }
}
