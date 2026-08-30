package com.portfolio.ticketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.ticketing.api.ApiException;
import com.portfolio.ticketing.api.ApiModels;
import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.OutboxEvent;
import com.portfolio.ticketing.domain.Payment;
import com.portfolio.ticketing.domain.SeatHold;
import com.portfolio.ticketing.domain.TicketOrder;
import com.portfolio.ticketing.repository.OutboxEventRepository;
import com.portfolio.ticketing.repository.PaymentRepository;
import com.portfolio.ticketing.repository.SeatHoldRepository;
import com.portfolio.ticketing.repository.TicketOrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final TicketOrderRepository orders;
    private final SeatHoldRepository holds;
    private final PaymentRepository payments;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final AvailabilityService availability;
    private final AuditService audit;
    private final IdempotencyKeyLock idempotencyKeys;
    private final Clock clock;
    private final Counter failures;

    public PaymentService(
            TicketOrderRepository orders,
            SeatHoldRepository holds,
            PaymentRepository payments,
            OutboxEventRepository outbox,
            ObjectMapper objectMapper,
            AvailabilityService availability,
            AuditService audit,
            IdempotencyKeyLock idempotencyKeys,
            Clock clock,
            MeterRegistry registry) {
        this.orders = orders;
        this.holds = holds;
        this.payments = payments;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.availability = availability;
        this.audit = audit;
        this.idempotencyKeys = idempotencyKeys;
        this.clock = clock;
        this.failures = registry.counter("payment_failure_total");
    }

    @Transactional
    public ApiModels.PaymentResponse capture(UUID orderId, String idempotencyKey, UUID userId) {
        validateKey(idempotencyKey);
        Payment firstRead = payments.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (firstRead != null) {
            requireSameRequest(firstRead, orderId, userId);
            return response(firstRead);
        }

        idempotencyKeys.acquire(idempotencyKey);
        Payment afterKeyLock = payments.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (afterKeyLock != null) {
            requireSameRequest(afterKeyLock, orderId, userId);
            return response(afterKeyLock);
        }

        TicketOrder order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found"));
        requireOwner(order, userId);

        Payment afterLock = payments.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (afterLock != null) {
            requireSameRequest(afterLock, orderId, userId);
            return response(afterLock);
        }

        if (order.getStatus() != DomainTypes.OrderStatus.PENDING) {
            failures.increment();
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "Order is not awaiting payment");
        }

        SeatHold hold = holds.findByIdForUpdate(order.getHold().getId()).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT, "HOLD_NOT_FOUND", "Order hold was not found"));
        if (hold.getStatus() != DomainTypes.HoldStatus.ACTIVE || hold.isExpired(clock.instant())) {
            failures.increment();
            throw new ApiException(HttpStatus.CONFLICT, "HOLD_EXPIRED", "Seat hold expired before payment");
        }

        Payment payment = payments.save(new Payment(order, idempotencyKey));
        hold.confirm();
        hold.getSeat().sell();
        order.confirm();
        outbox.save(new OutboxEvent(order.getId(), "ORDER_CONFIRMED", orderConfirmedPayload(order, payment)));
        audit.record(order.getUser(), "PAYMENT_CAPTURED", "Order", order.getId(),
                "{\"status\":\"PENDING\"}", "{\"status\":\"CONFIRMED\"}");
        availability.invalidate(hold.getSeat().getSession().getId());
        return response(payment);
    }

    @Transactional
    public ApiModels.PaymentResponse refund(UUID orderId, UUID userId, boolean adminAllowed) {
        TicketOrder order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found"));
        if (!order.getUser().getId().equals(userId) && !adminAllowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "You cannot refund this order");
        }
        Payment payment = payments.findFirstByOrderIdOrderByCapturedAtDesc(orderId).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT, "PAYMENT_NOT_FOUND", "Captured payment was not found"));
        if (order.getStatus() != DomainTypes.OrderStatus.REFUNDED) {
            SeatHold hold = holds.findByIdForUpdate(order.getHold().getId()).orElseThrow(() -> new ApiException(
                    HttpStatus.CONFLICT, "HOLD_NOT_FOUND", "Order hold was not found"));
            order.refund();
            payment.refund();
            hold.getSeat().reopenAfterRefund();
            audit.record(order.getUser(), "PAYMENT_REFUNDED", "Order", order.getId(),
                    "{\"status\":\"CONFIRMED\"}", "{\"status\":\"REFUNDED\"}");
            availability.invalidate(hold.getSeat().getSession().getId());
        }
        return response(payment);
    }

    private String orderConfirmedPayload(TicketOrder order, Payment payment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID());
        payload.put("orderId", order.getId());
        payload.put("paymentId", payment.getId());
        payload.put("recipient", order.getUser().getEmail());
        payload.put("ticketCode", order.getTicketCode());
        payload.put("occurredAt", clock.instant());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize order confirmation", exception);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain between 1 and 128 characters");
        }
    }

    private void requireSameRequest(Payment payment, UUID orderId, UUID userId) {
        if (!payment.getOrder().getId().equals(orderId)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was already used for a different order");
        }
        requireOwner(payment.getOrder(), userId);
    }

    private void requireOwner(TicketOrder order, UUID userId) {
        if (!order.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "You do not own this order");
        }
    }

    private ApiModels.PaymentResponse response(Payment payment) {
        return new ApiModels.PaymentResponse(
                payment.getId(), payment.getOrder().getId(), payment.getAmount(), payment.getStatus(),
                payment.getCapturedAt(), payment.getRefundedAt());
    }
}
