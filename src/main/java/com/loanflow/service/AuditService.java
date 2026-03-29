package com.loanflow.service;

import com.loanflow.dto.request.AuditRequest;

public interface AuditService {

    void log(AuditRequest build);
}
