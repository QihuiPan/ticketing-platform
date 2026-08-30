package com.portfolio.ticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class TicketOrder extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hold_id", nullable = false, unique = true)
    private SeatHold hold;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DomainTypes.OrderStatus status;

    @Column(name = "ticket_code", unique = true, length = 128)
    private String ticketCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketOrder() {
    }

    public TicketOrder(UserAccount user, SeatHold hold) {
        this.user = user;
        this.hold = hold;
        this.amount = hold.getSeat().getPrice();
        this.status = DomainTypes.OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UserAccount getUser() {
        return user;
    }

    public SeatHold getHold() {
        return hold;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public DomainTypes.OrderStatus getStatus() {
        return status;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void confirm() {
        if (status != DomainTypes.OrderStatus.PENDING) {
            throw new IllegalStateException("Only a pending order can be confirmed");
        }
        status = DomainTypes.OrderStatus.CONFIRMED;
        ticketCode = "TKT-" + UUID.randomUUID();
        updatedAt = Instant.now();
    }

    public void refund() {
        if (status == DomainTypes.OrderStatus.REFUNDED) {
            return;
        }
        if (status != DomainTypes.OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed order can be refunded");
        }
        status = DomainTypes.OrderStatus.REFUNDED;
        updatedAt = Instant.now();
    }
}
