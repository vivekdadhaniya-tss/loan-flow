package com.loanflow.repository;

import com.loanflow.entity.AuditLog;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog , UUID> {

    // Example of a custom paginated query (if you want to filter by action in the future)
//    Page<AuditLog> findByAction(String action, Pageable pageable);

}
