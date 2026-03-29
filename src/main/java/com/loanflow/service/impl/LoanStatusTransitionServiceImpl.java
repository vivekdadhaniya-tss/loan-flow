package com.loanflow.service.impl;

import com.loanflow.entity.Loan;
import com.loanflow.entity.LoanStatusHistory;
import com.loanflow.entity.user.User;
import com.loanflow.enums.LoanStatus;
import com.loanflow.exception.InvalidStatusTransitionException;
import com.loanflow.repository.LoanStatusHistoryRepository;
import com.loanflow.service.LoanStatusTransitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoanStatusTransitionServiceImpl implements LoanStatusTransitionService {

    private final LoanStatusHistoryRepository loanStatusHistoryRepository;

    private static final Map<LoanStatus, Set<LoanStatus>> VALID_TRANSITIONS = Map.of(
            LoanStatus.ACTIVE,      Set.of(LoanStatus.CLOSED, LoanStatus.DEFAULTED),
            LoanStatus.DEFAULTED,   Set.of(LoanStatus.ACTIVE,  LoanStatus.WRITTEN_OFF),
            LoanStatus.CLOSED,      Set.of(),   // terminal
            LoanStatus.WRITTEN_OFF, Set.of()    // terminal
    );

    public void transition(
            Loan loan, LoanStatus newStatus,
            User changeBy, String reason) {

        Set<LoanStatus> allowed = VALID_TRANSITIONS.getOrDefault(loan.getStatus(), Set.of());

        if (!allowed.contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition Loan from " + loan.getStatus() + " to " + newStatus);
        }

        LoanStatus old = loan.getStatus();
        loan.setStatus(newStatus);

        LoanStatusHistory history = LoanStatusHistory.builder()
                .loan(loan)
                .oldStatus(old)
                .newStatus(newStatus)
                .changedBy(changeBy)    // null when system
                .reason(reason)
                .changedAt(LocalDateTime.now())
                .build();
    }
}
