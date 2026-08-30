package com.portfolio.ticketing.repository;

import com.portfolio.ticketing.domain.SeatEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<SeatEntity, UUID> {

    List<SeatEntity> findBySessionIdOrderByLabel(UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from SeatEntity s
            join fetch s.session session
            join fetch session.event
            where s.id = :seatId
            """)
    Optional<SeatEntity> findByIdForUpdate(@Param("seatId") UUID seatId);
}
