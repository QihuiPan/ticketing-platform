package com.portfolio.ticketing.repository;

import com.portfolio.ticketing.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findFirstByOrderIdOrderByCapturedAtDesc(UUID orderId);
}
