package com.loanflow.service.impl;

import com.loanflow.dto.request.AuditRequest;
import com.loanflow.dto.request.LoanDecisionRequest;
import com.loanflow.dto.response.LoanResponse;
import com.loanflow.entity.Loan;
import com.loanflow.entity.LoanApplication;
import com.loanflow.entity.user.User;
import com.loanflow.enums.*;
import com.loanflow.event.LoanClosedEvent;
import com.loanflow.event.LoanDecisionEvent;
import com.loanflow.exception.BusinessRuleException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.mapper.LoanMapper;
import com.loanflow.repository.EmiScheduleRepository;
import com.loanflow.repository.LoanApplicationRepository;
import com.loanflow.repository.LoanRepository;
import com.loanflow.service.*;
import com.loanflow.strategy.EmiCalculationStrategy;
import com.loanflow.strategy.LoanStrategyFactory;
import com.loanflow.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanServiceImpl implements LoanService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepository loanRepository;
    private final EmiScheduleRepository emiScheduleRepository;
    private final EmiScheduleService emiScheduleService;
    private final DtiCalculationService dtiCalculationService;
    private final LoanStatusTransitionService loanStatusTransitionService;
    private final LoanStrategyFactory loanStrategyFactory;
    private final AuditService auditService;
    private final LoanMapper loanMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public LoanResponse processDecision(
            String applicationNumber, LoanDecisionRequest request, User officer) {

        // fetch application and validate
        LoanApplication application = loanApplicationRepository
                .findByApplicationNumber(applicationNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Loan Application not found: " + applicationNumber));

        ValidationUtil.ensureApplicationIsReviewable(application);

        application.setReviewedBy(officer);
        application.setReviewedAt(LocalDateTime.now());
        String oldStatus = application.getStatus().name();

        // if officer explicitly rejected it manually
        if (!request.getApproved()) {
            return executeRejection(application, request.getRejectionReason(), officer, oldStatus);
        }

        // approve application
        LoanStrategy finalStrategy = request.getOverrideStrategy() != null
                ? request.getOverrideStrategy()
                : application.getSuggestedStrategy();

        if (finalStrategy == null) {
            throw new BusinessRuleException("No valid loan strategy available");
        }

        BigDecimal internalEmi = loanRepository.sumActiveMonthlyEmi(application.getBorrower().getId()).orElse(BigDecimal.ZERO);
        BigDecimal externalEmi = application.getExistingMonthlyEmi();

        // Create a temporary loan object in memory just to calculate the EMI (Do NOT save it yet)
        Loan tempLoan = new Loan();
        tempLoan.setApprovedAmount(application.getRequestedAmount());
        tempLoan.setInterestRatePerAnnum(request.getInterestRatePerAnnum());
        tempLoan.setTenureMonths(application.getTenureMonths());

        EmiCalculationStrategy strategy = loanStrategyFactory.resolve(finalStrategy);
        BigDecimal baseEmi = strategy.calculateBaseEmi(tempLoan);

        // Calculate DTI
        BigDecimal dtiFinal = dtiCalculationService.calculateFinalDti(
                internalEmi, externalEmi, baseEmi, application.getMonthlyIncome());

        // AUTO-REJECT IF DTI > 40% (Overrides the Officer's approval)
        if (dtiFinal.compareTo(new BigDecimal("40.00")) > 0) {
            String autoRejectReason = String.format("System Auto-Reject: Final DTI with new loan exceeds 40%% (Calculated: %.2f%%)", dtiFinal);
            log.warn("Application {} automatically rejected by system due to high DTI: {}%", applicationNumber, dtiFinal);
            return executeRejection(application, autoRejectReason, officer, oldStatus);
        }

        application.setFinalStrategy(finalStrategy);
        application.setStatus(ApplicationStatus.APPROVED);
        loanApplicationRepository.save(application);

        // Now finalize the loan details
        tempLoan.setLoanNumber(generateLoanNumber());
        tempLoan.setApplication(application);
        tempLoan.setBorrower(application.getBorrower());
        tempLoan.setApprovedBy(officer);
        tempLoan.setStrategy(finalStrategy);
        tempLoan.setStatus(LoanStatus.ACTIVE);
        tempLoan.setDisbursedAt(LocalDateTime.now());
        tempLoan.setOutstandingPrincipal(application.getRequestedAmount());
        tempLoan.setMonthlyEmi(baseEmi);

        Loan savedLoan = loanRepository.save(tempLoan);

        // generate and persist the full amortization schedule
        emiScheduleService.generateSchedule(savedLoan, strategy);

        log.info("Loan {} approved successfully - DTI_final: {}%", savedLoan.getId(), dtiFinal);

        // Audit for loan application
        auditService.log(AuditRequest.builder()
                .entityType(EntityType.LOAN_APPLICATION)
                .entityId(application.getId())
                .action("APPROVED")
                .oldStatus(oldStatus)
                .newStatus(ApplicationStatus.APPROVED.name())
                .performedBy(officer)
                .actorRole(officer.getRole())
                .remarks("Strategy: " + finalStrategy + ", DTI_final: " + dtiFinal + "%")
                .build());

        // Audit for loan creation
        auditService.log(AuditRequest.builder()
                .entityType(EntityType.LOAN)
                .entityId(savedLoan.getId())
                .action("CREATED")
                .newStatus(LoanStatus.ACTIVE.name())
                .performedBy(officer)
                .actorRole(officer.getRole())
                .remarks("Loan created from application " + application.getId())
                .build());

        eventPublisher.publishEvent(new LoanDecisionEvent(savedLoan, application, ApplicationStatus.APPROVED, null));

        return loanMapper.toResponse(savedLoan);
    }

    private LoanResponse executeRejection(LoanApplication application, String reason, User officer, String oldStatus) {
        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectionReason(reason);
        loanApplicationRepository.save(application);

        auditService.log(AuditRequest.builder()
                .entityType(EntityType.LOAN_APPLICATION)
                .entityId(application.getId())
                .action("REJECTED")
                .oldStatus(oldStatus)
                .newStatus(ApplicationStatus.REJECTED.name())
                .performedBy(officer)
                .actorRole(officer.getRole())
                .remarks(reason)
                .build());

        eventPublisher.publishEvent(new LoanDecisionEvent(null, application, ApplicationStatus.REJECTED, reason));

        return null;   // No loan created, returns null
    }

    private String generateLoanNumber() {
        Long seqVal = loanRepository.getNextLoanSequence();

        if (seqVal == null)
            throw new IllegalStateException("Failed to generate loan number sequence");

        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("LN-%s-%06d", datePrefix, seqVal);
    }

    @Override
    @Transactional
    public void closeLoanIfCompleted(Loan loan) {
        Long unpaidCount = emiScheduleRepository
                .countByLoanAndStatusNot(loan, EmiStatus.PAID);

        if (unpaidCount == 0) {
            loanStatusTransitionService.transition(
                    loan, LoanStatus.CLOSED, null, "All EMIs paid");
            loan.setClosedAt(LocalDateTime.now());
            loanRepository.save(loan);

            eventPublisher.publishEvent(new LoanClosedEvent(loan));
            log.info("Loan {} closed as all EMIs are paid", loan.getId());
        }
    }

    @Override
    public List<LoanResponse> getMyLoans(Long borrowerId) {
        List<Loan> loans = loanRepository.findByBorrowerIdOrderByCreatedAtDesc(borrowerId);
        return loanMapper.toResponseList(loans);
    }

    @Override
    public Loan findById(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));
    }

    @Override
    public Loan findByLoanNumber(String loanNumber) {
        return loanRepository.findByLoanNumber(loanNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanNumber));
    }

}
