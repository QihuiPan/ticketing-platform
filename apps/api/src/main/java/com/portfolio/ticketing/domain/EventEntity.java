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
@Table(name = "events")
public class EventEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private UserAccount organizer;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DomainTypes.EventStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EventEntity() {
    }

    public EventEntity(UserAccount organizer, String title, String description) {
        this.organizer = organizer;
        this.title = title;
        this.description = description;
        this.status = DomainTypes.EventStatus.DRAFT;
        this.createdAt = Instant.now();
    }

    public UserAccount getOrganizer() {
        return organizer;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public DomainTypes.EventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void publish() {
        if (status == DomainTypes.EventStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled event cannot be published");
        }
        status = DomainTypes.EventStatus.PUBLISHED;
    }

    public void cancel() {
        status = DomainTypes.EventStatus.CANCELLED;
    }
}
