package com.loanflow.service.impl;

import com.loanflow.dto.response.AuditLogResponse;
import com.loanflow.entity.AuditLog;
import com.loanflow.entity.user.User;
import com.loanflow.enums.EntityType;
import com.loanflow.enums.Role;
import com.loanflow.mapper.AuditLogMapper;
import com.loanflow.repository.AuditLogRepository;
import com.loanflow.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(EntityType entityName,
                    UUID entityId,
                    String action,
                    String oldValue,
                    String newValue,
                    User performedBy,
                    Role userRole,
                    String remarks) {

        // 1. Create a new AuditLog entity
        AuditLog auditLog = new AuditLog();
        auditLog.setEntityType(entityName);     // e.g., LOAN_APPLICATION
        auditLog.setEntityId(entityId);         // e.g., The UUID of the application
        auditLog.setAction(action);             // e.g., "SUBMITTED" or "CANCELLED"
        auditLog.setOldStatus(oldValue);         // e.g., "PENDING"
        auditLog.setNewStatus(newValue);         // e.g., "CANCELLED"
        auditLog.setPerformedBy(performedBy);   // The User object doing the action
        auditLog.setActorRole(userRole);         // e.g., BORROWER or OFFICER
        auditLog.setRemarks(remarks);           // e.g., "Application cancelled by borrower."

        // 2. Save it to the database
        auditLogRepository.save(auditLog);

        log.debug("Audit Log recorded: [{}] {} on {} ({}) by {}",
                action, entityName, entityId, remarks, performedBy.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAllAuditLogs(Pageable pageable) {
        // Fetch the paginated entities from the database
        Page<AuditLog> auditLogPage = auditLogRepository.findAll(pageable);

        // Spring Data's Page object has a built-in .map() function to convert Entities to DTOs!
        return auditLogPage.map(auditLogMapper::toResponse);
    }
}
