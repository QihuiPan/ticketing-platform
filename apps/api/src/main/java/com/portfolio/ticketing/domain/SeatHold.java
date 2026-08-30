package com.portfolio.ticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "seat_holds")
public class SeatHold extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private SeatEntity seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DomainTypes.HoldStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SeatHold() {
    }

    public SeatHold(SeatEntity seat, UserAccount user, Instant expiresAt) {
        this.seat = seat;
        this.user = user;
        this.expiresAt = expiresAt;
        this.status = DomainTypes.HoldStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public SeatEntity getSeat() {
        return seat;
    }

    public UserAccount getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public DomainTypes.HoldStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void release() {
        if (status == DomainTypes.HoldStatus.ACTIVE) {
            status = DomainTypes.HoldStatus.RELEASED;
        }
    }

    public void expire() {
        if (status == DomainTypes.HoldStatus.ACTIVE) {
            status = DomainTypes.HoldStatus.EXPIRED;
        }
    }

    public void confirm() {
        if (status != DomainTypes.HoldStatus.ACTIVE) {
            throw new IllegalStateException("Only an active hold can be confirmed");
        }
        status = DomainTypes.HoldStatus.CONFIRMED;
    }
}
