package com.loanflow.service;

import com.loanflow.dto.response.AuditLogResponse;
import com.loanflow.entity.user.User;
import com.loanflow.enums.EntityType;
import com.loanflow.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface AuditService {

//    void log(String loanApplication, UUID id, String cancelled, String oldStatus, String name, User borrower, String borrower1, String s);

    @Transactional(propagation = Propagation.MANDATORY)
    void log(EntityType entityName,
             UUID entityId,
             String action,
             String oldValue,
             String newValue,
             User performedBy,
             Role userRole,
             String remarks);

    @Transactional(readOnly = true)
    Page<AuditLogResponse> getAllAuditLogs(Pageable pageable);
}
