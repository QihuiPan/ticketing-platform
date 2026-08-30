package com.portfolio.ticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "seats")
public class SeatEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private EventSessionEntity session;

    @Column(nullable = false, length = 64)
    private String label;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DomainTypes.SeatStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    protected SeatEntity() {
    }

    public SeatEntity(EventSessionEntity session, String label, BigDecimal price) {
        this.session = session;
        this.label = label;
        this.price = price;
        this.status = DomainTypes.SeatStatus.AVAILABLE;
    }

    public EventSessionEntity getSession() {
        return session;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public DomainTypes.SeatStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public void hold() {
        if (status != DomainTypes.SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat is not available");
        }
        status = DomainTypes.SeatStatus.HELD;
    }

    public void release() {
        if (status == DomainTypes.SeatStatus.HELD) {
            status = DomainTypes.SeatStatus.AVAILABLE;
        }
    }

    public void sell() {
        if (status != DomainTypes.SeatStatus.HELD) {
            throw new IllegalStateException("Only a held seat can be sold");
        }
        status = DomainTypes.SeatStatus.SOLD;
    }

    public void reopenAfterRefund() {
        if (status != DomainTypes.SeatStatus.SOLD) {
            throw new IllegalStateException("Only a sold seat can be reopened after a refund");
        }
        status = DomainTypes.SeatStatus.AVAILABLE;
    }
}
