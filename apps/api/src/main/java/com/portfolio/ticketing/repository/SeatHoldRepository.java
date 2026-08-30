package com.portfolio.ticketing.repository;

import com.portfolio.ticketing.domain.DomainTypes;
import com.portfolio.ticketing.domain.SeatHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {

    Optional<SeatHold> findBySeatIdAndStatus(UUID seatId, DomainTypes.HoldStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SeatHold h join fetch h.seat join fetch h.user where h.id = :holdId")
    Optional<SeatHold> findByIdForUpdate(@Param("holdId") UUID holdId);

    @Query(value = """
            SELECT * FROM seat_holds
            WHERE status = 'ACTIVE' AND expires_at <= :now
            ORDER BY expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SeatHold> findExpiredForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize);
}
