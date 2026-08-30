package com.portfolio.ticketing.domain;

public final class DomainTypes {

    private DomainTypes() {
    }

    public enum Role {
        BUYER,
        ORGANIZER,
        ADMIN
    }

    public enum EventStatus {
        DRAFT,
        PUBLISHED,
        CANCELLED
    }

    public enum SeatStatus {
        AVAILABLE,
        HELD,
        SOLD
    }

    public enum HoldStatus {
        ACTIVE,
        RELEASED,
        EXPIRED,
        CONFIRMED
    }

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        CANCELLED,
        REFUNDED
    }

    public enum PaymentStatus {
        CAPTURED,
        REFUNDED
    }
}
