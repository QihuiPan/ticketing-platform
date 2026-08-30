package com.portfolio.ticketing.service;

import com.portfolio.ticketing.domain.AuditLog;
import com.portfolio.ticketing.domain.UserAccount;
import com.portfolio.ticketing.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    public void record(
            UserAccount actor,
            String action,
            String entityType,
            UUID entityId,
            String beforeState,
            String afterState) {
        logs.save(new AuditLog(actor, action, entityType, entityId, beforeState, afterState));
    }
}
