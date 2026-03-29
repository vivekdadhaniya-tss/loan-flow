package com.loanflow.repository;

import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;
import com.loanflow.enums.EmiStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmiScheduleRepository extends JpaRepository<EmiSchedule, UUID> {

    // Amortization table for a loan
    List<EmiSchedule> findByLoanOrderByInstallmentNumberAsc(Loan loan);

    // Daily overdue scanner
    List<EmiSchedule> findByStatusAndDueDateBefore(EmiStatus status, LocalDate date);

    // Payment reminder: PENDING EMIs due in exactly N days
    // Called with from = today+3, to = today+3 (same day range)
    List<EmiSchedule> findByStatusAndDueDateBetween(EmiStatus status, LocalDate from, LocalDate to);

    // Used by PaymentService to check if all EMIs are PAID
    Long countByLoanAndStatusNot(Loan loan, EmiStatus status);
}
