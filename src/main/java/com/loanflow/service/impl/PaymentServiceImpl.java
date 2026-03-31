package com.loanflow.service.impl;

import com.loanflow.dto.request.AuditRequest;
import com.loanflow.dto.request.PaymentSimulationRequest;
import com.loanflow.dto.response.PaymentResponse;
import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;
import com.loanflow.entity.Payment;
import com.loanflow.entity.user.User;
import com.loanflow.enums.EmiStatus;
import com.loanflow.enums.EntityType;
import com.loanflow.enums.LoanStatus;
import com.loanflow.enums.Role;
import com.loanflow.event.PaymentReceivedEvent;
import com.loanflow.exception.BusinessRuleException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.exception.UnauthorizedAccessException;
import com.loanflow.mapper.PaymentMapper;
import com.loanflow.repository.EmiScheduleRepository;
import com.loanflow.repository.LoanRepository;
import com.loanflow.repository.PaymentRepository;
import com.loanflow.service.AuditService;
import com.loanflow.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.el.lang.ELArithmetic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import java.time.*;
import java.time.format.*;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final LoanRepository loanRepository;
    private final EmiScheduleRepository emiScheduleRepository;
    private final AuditService auditService;

    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public PaymentResponse simulatePayment(PaymentSimulationRequest request, User borrower) {

        // 1. Fetch the exact EMI Schedule
        EmiSchedule schedule = emiScheduleRepository.findById(request.getEmiScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("EMI Schedule not found."));

        Loan loan= schedule.getLoan();

        // 2. Security Check: Ensure the borrower actually owns this loan
        if(!loan.getBorrower().getId().equals((borrower.getId()))){
            log.warn("User {} attempted to pay an EMI for a loan they do not own.", borrower.getEmail());
            throw new UnauthorizedAccessException("You are not authorized to make payments on this loan.");
        }

        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Payments can only be made on ACTIVE loans. " +
                            "Current status: " + loan.getStatus());
        }

        // 3. Business Rule: Prevent double payments
        // (Assuming your schedule entity uses an enum like ScheduleStatus.PAID)
        if (EmiStatus.PAID.equals(schedule.getStatus())) {
            throw new BusinessRuleException("This EMI installment has already been paid.");
        }

        // 4. Create the Payment Record
        Payment payment = new Payment();
        payment.setLoan(loan);
        payment.setEmiSchedule(schedule);
        payment.setBorrower(borrower);
        payment.setPaidAmount(schedule.getTotalEmiAmount());
        payment.setPaidAt(LocalDateTime.now());
        payment.setPaymentMode("SIMULATION");

        payment.setReceiptNumber(generateReceiptNumber());

        paymentRepository.save(payment);

        schedule.setStatus(EmiStatus.PAID);
        emiScheduleRepository.save(schedule);

        // 6. Update the Main Loan Balances
        // Deduct only the principal component of the EMI from the outstanding balance
        BigDecimal newOutstanding = loan.getOutstandingPrincipal().subtract(schedule.getPrincipalAmount());
        loan.setOutstandingPrincipal(newOutstanding);

        if(newOutstanding.compareTo(BigDecimal.ZERO) <= 0){
            loan.setStatus(LoanStatus.CLOSED);
            log.info("Loan {} has been fully paid off and is now CLOSED.", loan.getLoanNumber());
        }
        loanRepository.save(loan);

        auditService.log(AuditRequest.builder()
                .entityType(EntityType.PAYMENT)
                .entityId(payment.getId())
                .action("COMPLETED")
                .oldStatus(null)
                .newStatus("SUCCESS")
                .performedBy(borrower)
                .actorRole(Role.BORROWER)
                .remarks("EMI Payment simulated successfully via portal.")
                .build());

        eventPublisher.publishEvent(new PaymentReceivedEvent(payment, schedule));

        return paymentMapper.toResponse(payment);

    }


    /**
     * Generates a unique, chronological receipt number for a payment.
     * Example: RCP-20260329-00001
     */
    private String generateReceiptNumber() {
        Long seqVal = paymentRepository.getNextReceiptSequence();
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Pads the sequence with up to 5 zeros for a clean, professional look
        String paddedSequence = String.format("%05d", seqVal);

        return "RCP-" + datePrefix + "-" + paddedSequence;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByLoanNumber(String loanNumber) {
        // Fetch payments using the business key, ordered by latest first
        List<Payment> payments = paymentRepository.findByLoan_LoanNumberOrderByPaidAtDesc(loanNumber);

        return payments.stream()
                .map(paymentMapper::toResponse)
                .collect(Collectors.toList());
    }

}
