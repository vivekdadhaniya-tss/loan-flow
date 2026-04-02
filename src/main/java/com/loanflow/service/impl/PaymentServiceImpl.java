package com.loanflow.service.impl;

import com.loanflow.dto.request.AuditRequest;
import com.loanflow.dto.request.PaymentSimulationRequest;
import com.loanflow.dto.response.PaymentResponse;
import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;
import com.loanflow.entity.Payment;
import com.loanflow.entity.user.User;
import com.loanflow.enums.*;
import com.loanflow.event.PaymentReceivedEvent;
import com.loanflow.exception.BusinessRuleException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.exception.UnauthorizedAccessException;
import com.loanflow.mapper.PaymentMapper;
import com.loanflow.repository.EmiScheduleRepository;
import com.loanflow.repository.LoanRepository;
import com.loanflow.repository.OverdueTrackerRepository;
import com.loanflow.repository.PaymentRepository;
import com.loanflow.security.SecurityUtils;
import com.loanflow.service.AuditService;
import com.loanflow.service.LoanService;
import com.loanflow.service.PaymentService;
import com.loanflow.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.time.*;
import java.time.format.*;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final EmiScheduleRepository emiScheduleRepository;
    private final PaymentRepository paymentRepository;
    private final OverdueTrackerRepository overdueTrackerRepository;
    private final LoanRepository loanRepository;
    private final LoanService loanService;
    private final AuditService auditService;
    private final PaymentMapper paymentMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public List<PaymentResponse> simulatePayment(PaymentSimulationRequest request, User borrower) {

        Loan loan = loanRepository.findByLoanNumber(request.getLoanNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan not found: " + request.getLoanNumber()));

        if (!securityUtils.isOwner(loan.getBorrower().getId()) && !securityUtils.hasRole("ADMIN")) {
            throw new UnauthorizedAccessException("You are not authorized to make a payment for this loan.");
        }

        // Guard: Loan must be ACTIVE
        ValidationUtil.ensureLoanIsActive(loan);

        // Fetch unpaid EMIs sequentially
        List<EmiSchedule> unpaidEmis = emiScheduleRepository.findNextUnpaidEmis(
                loan, PageRequest.of(0, 50));

        if (unpaidEmis.isEmpty()) {
            throw new BusinessRuleException("No pending EMIs found for this loan.");
        }

        int requestedCount = request.getInstallmentCount() != null ? request.getInstallmentCount() : 1;

        // Validation Rule: max allowed is OVERDUE count + 3 (Current + 2 Upcomings)
        long overdueCount = unpaidEmis.stream()
                .filter(e -> e.getStatus() == EmiStatus.OVERDUE).count();
        int maxAllowed = (int) overdueCount + 3;

        if (requestedCount > maxAllowed) {
            throw new BusinessRuleException(
                    "You can only pay up to " + maxAllowed + " installments at a time (Overdue + Current + Next 2).");
        }

        if (requestedCount > unpaidEmis.size()) {
            requestedCount = unpaidEmis.size();
        }

        List<EmiSchedule> emisToPay = unpaidEmis.subList(0, requestedCount);
        List<Payment> savedPayments = new ArrayList<>();

        for (EmiSchedule emi : emisToPay) {
            String oldEmiStatus = emi.getStatus().name();

            emi.setStatus(EmiStatus.PAID);
            emi.setPaidAt(LocalDateTime.now());
            emiScheduleRepository.save(emi);

            Payment payment = new Payment();
            payment.setEmiSchedule(emi);
            payment.setLoan(loan);
            payment.setBorrower(borrower);
            payment.setPaidAmount(emi.getTotalEmiAmount());
            payment.setPaymentMode(PaymentMode.SIMULATION);
            payment.setPaidAt(LocalDateTime.now());
            payment.setReceiptNumber(generateReceiptNumber());
            Payment saved = paymentRepository.save(payment);
            savedPayments.add(saved);

            // --- Reduce the outstanding principal from Loan ---
            // Subtract only the principal part of the EMI from the loan balance
            BigDecimal newBalance = loan.getOutstandingPrincipal().subtract(emi.getPrincipalAmount());

            // Ensure the balance never goes below zero due to rounding differences
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                newBalance = BigDecimal.ZERO;
            }

            loan.setOutstandingPrincipal(newBalance);
            loanRepository.save(loan);

            if (EmiStatus.OVERDUE.name().equals(oldEmiStatus)) {
                overdueTrackerRepository.findByEmiSchedule(emi)
                        .ifPresent(tracker -> {
                            tracker.setResolvedAt(LocalDateTime.now());
                            tracker.setPenaltyStatus(PenaltyStatus.SETTLED);
                            overdueTrackerRepository.save(tracker);

                            loan.setOverDueCount(Math.max(0, loan.getOverDueCount() - 1));
                            loanRepository.save(loan);
                        });
            }

            auditService.log(AuditRequest.builder()
                    .entityType(EntityType.EMI_SCHEDULE)
                    .entityId(emi.getId())
                    .action("MARKED_PAID")
                    .oldStatus(oldEmiStatus)
                    .newStatus(EmiStatus.PAID.name())
                    .performedBy(borrower)
                    .actorRole(borrower.getRole())
                    .remarks("Payment simulated. Amount: " + emi.getTotalEmiAmount())
                    .build());

            applicationEventPublisher.publishEvent(new PaymentReceivedEvent(saved, emi));
        }

        loanService.closeLoanIfCompleted(loan);

        log.info("{} payments simulated for loan {}", savedPayments.size(), loan.getId());

        return savedPayments.stream()
                .map(paymentMapper::toResponse)
                .collect(Collectors.toList());
    }


    private String generateReceiptNumber() {
        Long seqVal = paymentRepository.getNextReceiptSequence();

        if (seqVal == null)
            throw new IllegalStateException("Failed to generate receipt number sequence");

        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        return String.format("RCP-%s-%05d", datePrefix, seqVal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByLoanNumber(String loanNumber) {

        Loan loan = loanRepository.findByLoanNumber(loanNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan not found with number: " + loanNumber));

        if (!securityUtils.hasRole("LOAN_OFFICER")) {
            // Only the borrower (or an Admin) can view payments for this loan
            if (!securityUtils.isOwner(loan.getBorrower().getId())) {
                throw new UnauthorizedAccessException(
                        "Access Denied: You do not have permission to view payments for this loan.");
            }
        }

        List<Payment> payments = paymentRepository.findByLoan_LoanNumberOrderByPaidAtDesc(loanNumber);

        return payments.stream()
                .map(paymentMapper::toResponse)
                .collect(Collectors.toList());
    }

}