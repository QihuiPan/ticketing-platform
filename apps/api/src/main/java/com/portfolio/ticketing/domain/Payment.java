package com.portfolio.ticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private TicketOrder order;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DomainTypes.PaymentStatus status;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    protected Payment() {
    }

    public Payment(TicketOrder order, String idempotencyKey) {
        this.order = order;
        this.idempotencyKey = idempotencyKey;
        this.amount = order.getAmount();
        this.status = DomainTypes.PaymentStatus.CAPTURED;
        this.capturedAt = Instant.now();
    }

    public TicketOrder getOrder() {
        return order;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public DomainTypes.PaymentStatus getStatus() {
        return status;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public void refund() {
        if (status == DomainTypes.PaymentStatus.REFUNDED) {
            return;
        }
        status = DomainTypes.PaymentStatus.REFUNDED;
        refundedAt = Instant.now();
    }
}
