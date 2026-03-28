package com.loanflow.service;

import com.loanflow.entity.user.User;

import java.util.UUID;

public interface AuditService {

    void log(String loanApplication, UUID id, String cancelled, String oldStatus, String name, User borrower, String borrower1, String s);
}
