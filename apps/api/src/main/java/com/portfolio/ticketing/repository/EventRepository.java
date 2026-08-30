package com.portfolio.ticketing.repository;

import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.EventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    Page<EventEntity> findByStatusAndTitleContainingIgnoreCase(
            DomainTypes.EventStatus status, String title, Pageable pageable);
}
