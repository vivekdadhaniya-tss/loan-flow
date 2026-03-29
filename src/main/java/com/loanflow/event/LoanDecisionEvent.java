package com.loanflow.event;

import com.loanflow.entity.*;
import com.loanflow.enums.ApplicationStatus;
import lombok.*;
@Getter @AllArgsConstructor
public class LoanDecisionEvent {
    private final Loan loan;          // null on rejection
    private final LoanApplication application;
    private final ApplicationStatus decision;
    private final String rejectionReason;
}
