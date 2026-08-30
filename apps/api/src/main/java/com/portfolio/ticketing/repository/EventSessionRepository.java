package com.portfolio.ticketing.repository;

import com.portfolio.ticketing.domain.EventSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventSessionRepository extends JpaRepository<EventSessionEntity, UUID> {

    List<EventSessionEntity> findByEventIdOrderByStartsAt(UUID eventId);
}
