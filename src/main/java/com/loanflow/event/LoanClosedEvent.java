package com.loanflow.event;

import com.loanflow.entity.Loan;
import lombok.*;

@Getter @AllArgsConstructor
public class LoanClosedEvent {
    private final Loan loan;
}
