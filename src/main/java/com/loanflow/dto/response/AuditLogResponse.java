package com.loanflow.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLogResponse {

    private UUID id;
    private String entityType;
    private UUID entityId;
    private String action;
    private String oldStatus;
    private String newStatus;

    /** Raw JSON diff — frontend pretty-prints */
    private String changeDetailJson;

    /** Null when actorRole = SYSTEM */
    private String performedByName;

    private String actorRole;
    private String remarks;
    private LocalDateTime createdAt;
}
