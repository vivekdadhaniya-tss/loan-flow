package com.loanflow.service.impl;

import com.loanflow.dto.request.LoanApplicationRequest;
import com.loanflow.dto.response.LoanApplicationResponse;
import com.loanflow.entity.LoanApplication;
import com.loanflow.entity.user.BorrowerProfile;
import com.loanflow.entity.user.User;
import com.loanflow.enums.*;
import com.loanflow.integration.CreditBureauClient;
import com.loanflow.integration.CreditBureauService;
import com.loanflow.integration.CreditBureauServiceImpl;
import com.loanflow.mapper.LoanApplicationMapper;
import com.loanflow.repository.LoanApplicationRepository;
import com.loanflow.service.LoanApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;


import com.loanflow.constants.LoanConstants;
import com.loanflow.exception.BusinessRuleException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.integration.dto.CreditBureauResponse;
import com.loanflow.repository.LoanRepository;
import com.loanflow.service.AuditService;
import com.loanflow.service.DtiCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationServiceImpl implements LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepository loanRepository;
    private final LoanApplicationMapper loanApplicationMapper;
    private final DtiCalculationService dtiCalculationService;
    private final CreditBureauService creditBureauService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final CreditBureauClient creditBureauClient;

    @Override
    @Transactional
    public LoanApplicationResponse apply(LoanApplicationRequest request, User borrower) {
        BorrowerProfile currentBorrower = (BorrowerProfile) borrower;

        // 1. max 3 Active Loans are allowed
        long activeLoansCount = loanRepository.countByBorrowerAndStatus(currentBorrower, LoanStatus.ACTIVE);
        if (activeLoansCount >= LoanConstants.MAX_ACTIVE_LOANS) {
            throw new BusinessRuleException("You have reached the maximum allowed limit of " + LoanConstants.MAX_ACTIVE_LOANS + " active loans.");
        }

        // 2. Check Business Rule: No overlapping pending applications
        boolean hasPending = loanApplicationRepository.existsByBorrowerAndStatusIn(
                currentBorrower, List.of(ApplicationStatus.PENDING, ApplicationStatus.UNDER_REVIEW));
        if (hasPending) {
            throw new BusinessRuleException("You already have a loan application under review.");
        }

        LoanApplication application = new LoanApplication();
        application.setBorrower(currentBorrower);
        application.setRequestedAmount(request.getRequestedAmount());
        application.setTenureMonths(request.getTenureMonths());
        application.setMonthlyIncome(request.getMonthlyIncome());

        // 3. Fetch External Credit Bureau Data (USE THE SERVICE, NOT THE CLIENT DIRECTLY)
        CreditBureauServiceImpl.ExternalDebtResult bureauResult = creditBureauService.fetchExternalEmi(currentBorrower.getPanNumber());

        // Assuming BureauStatus is an Enum based on your code snippet
        if (LoanConstants.BUREAU_STATUS_AVAILABLE.equals(bureauResult.bureauStatus())) {
            application.setBureauStatus(BureauStatus.FETCHED);
        } else {
            application.setBureauStatus(BureauStatus.UNAVAILABLE);
        }

        // 4. Calculate DTI and Suggest Strategy
        if (application.getStatus() != ApplicationStatus.REJECTED) {

            BigDecimal internalEmi = loanRepository.sumActiveMonthlyEmiByBorrower(currentBorrower.getId())
                    .orElse(BigDecimal.ZERO);

            // Extract the BigDecimal directly from the record we got in Step 3!
            // No need for .max() because we removed self-declared EMI.
            BigDecimal finalExternalEmi = bureauResult.externalMonthlyEmi();

            application.setExistingMonthlyEmi(finalExternalEmi);

            // Calculate DTI
            BigDecimal dti = dtiCalculationService.calculateInitialDti(internalEmi, finalExternalEmi, request.getMonthlyIncome());
            application.setCalculatedDti(dti);

            LoanStrategy suggestedStrategy = dtiCalculationService.suggestStrategy(dti, request.getTenureMonths());

            if (suggestedStrategy == null) {
                application.setStatus(ApplicationStatus.REJECTED);
                application.setRejectionReason("Auto-rejected due to high Debt-to-Income (DTI) ratio (>40%).");
            } else {
                application.setSuggestedStrategy(suggestedStrategy);
                application.setStatus(ApplicationStatus.PENDING);
            }
        }

        // 5. Generate Guaranteed Unique Application Number
        Long seqVal = loanApplicationRepository.getNextApplicationSequence();
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        application.setApplicationNumber("APP-" + datePrefix + "-" + seqVal);

        // 6. Save to Database
        LoanApplication savedApplication = loanApplicationRepository.save(application);

        // 7. Audit Logging
        auditService.log(
                EntityType.LOAN_APPLICATION,
                savedApplication.getId(),
                "SUBMITTED",
                null,
                savedApplication.getStatus().name(),
                currentBorrower,
                Role.BORROWER,
                "Application submitted via portal."
        );

        // 8. Publish Event (for async email notifications, etc.)
        // eventPublisher.publishEvent(new LoanApplicationSubmittedEvent(savedApplication));

        return loanApplicationMapper.toResponse(savedApplication);
    }

    @Override
    @Transactional
    public void cancelApplication(String applicationNumber, User borrower) {

        LoanApplication application = loanApplicationRepository.findByApplicationNumber(applicationNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with number: " + applicationNumber));

        if (!application.getBorrower().getId().equals(borrower.getId())) {
            throw new BusinessRuleException("You do not have permission to cancel this application.");
        }

        // cancel Rules
        if (application.getStatus() != ApplicationStatus.PENDING && application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("Only PENDING or UNDER_REVIEW applications can be cancelled.");
        }

        String oldStatus = application.getStatus().name();
        application.setStatus(ApplicationStatus.CANCELLED);
        loanApplicationRepository.save(application);

        auditService.log(
                EntityType.LOAN_APPLICATION,
                application.getId(),
                "CANCELLED",
                oldStatus,
                ApplicationStatus.CANCELLED.name(),
                borrower,
                Role.BORROWER,
                "Application cancelled by borrower."
        );

        log.info("Application {} successfully cancelled by user {}", applicationNumber, borrower.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanApplicationResponse> getMyApplications(User borrower) {
        // fetches only the applications belonging to the current user
        List<LoanApplication> applications = loanApplicationRepository.findByBorrower(borrower);

        return applications.stream()
                .map(loanApplicationMapper::toResponse)
                .collect(Collectors.toList());
    }
}
