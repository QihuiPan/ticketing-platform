package com.portfolio.ticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "event_sessions")
public class EventSessionEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private EventEntity event;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false, length = 200)
    private String venue;

    protected EventSessionEntity() {
    }

    public EventSessionEntity(EventEntity event, Instant startsAt, String venue) {
        this.event = event;
        this.startsAt = startsAt;
        this.venue = venue;
    }

    public EventEntity getEvent() {
        return event;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public String getVenue() {
        return venue;
    }
}
