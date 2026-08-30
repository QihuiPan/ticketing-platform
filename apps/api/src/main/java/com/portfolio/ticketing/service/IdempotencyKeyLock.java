package com.portfolio.ticketing.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyLock {

    @PersistenceContext
    private EntityManager entityManager;

    public void acquire(String idempotencyKey) {
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtextextended(cast(:key as text), 0))")
                .setParameter("key", idempotencyKey)
                .getSingleResult();
    }
}
