package com.portfolio.ticketing.repository;

import com.portfolio.ticketing.domain.TicketOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, UUID> {

    Optional<TicketOrder> findByHoldId(UUID holdId);

    List<TicketOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from TicketOrder o
            join fetch o.user
            join fetch o.hold h
            join fetch h.seat
            where o.id = :orderId
            """)
    Optional<TicketOrder> findByIdForUpdate(@Param("orderId") UUID orderId);
}
