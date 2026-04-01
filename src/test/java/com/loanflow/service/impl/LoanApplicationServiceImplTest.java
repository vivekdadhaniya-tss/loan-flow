package com.loanflow.service.impl;

import com.loanflow.constants.LoanConstants;
import com.loanflow.dto.request.AuditRequest;
import com.loanflow.dto.request.LoanApplicationRequest;
import com.loanflow.dto.response.BorrowerApplicationResponse;
//import com.loanflow.dto.response.LoanApplicationResponse;
import com.loanflow.dto.response.OfficerApplicationResponse;
import com.loanflow.entity.LoanApplication;
import com.loanflow.entity.user.Borrower;
import com.loanflow.entity.user.User;
import com.loanflow.enums.*;
import com.loanflow.event.LoanApplicationSubmittedEvent;
import com.loanflow.exception.BusinessRuleException;
import com.loanflow.exception.LoanLimitExceededException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.exception.UnauthorizedAccessException;
import com.loanflow.integration.CreditBureauService;
import com.loanflow.integration.CreditBureauServiceImpl.ExternalDebtResult;
import com.loanflow.mapper.LoanApplicationMapper;
import com.loanflow.repository.LoanApplicationRepository;
import com.loanflow.repository.LoanRepository;
import com.loanflow.service.AuditService;
import com.loanflow.service.DtiCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceImplTest {

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private DtiCalculationService dtiCalculationService;
    @Mock private CreditBureauService creditBureauService;
    @Mock private AuditService auditService;
    @Mock private LoanApplicationMapper loanApplicationMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LoanApplicationServiceImpl loanApplicationService;

    @Captor
    private ArgumentCaptor<LoanApplication> applicationCaptor;

    private Borrower testBorrower;
    private LoanApplicationRequest applyRequest;

    @BeforeEach
    void setUp() {
        testBorrower = new Borrower();
        testBorrower.setId(100L);
        testBorrower.setPanNumber("ABCDE1234F");
        testBorrower.setRole(Role.BORROWER);

        applyRequest = new LoanApplicationRequest();
        applyRequest.setRequestedAmount(new BigDecimal("100000"));
        applyRequest.setTenureMonths(12);
        applyRequest.setMonthlyIncome(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("Should throw Exception when Max Active Loans exceeded")
    void apply_FailsGuard1_MaxLoansExceeded() {
        // Arrange
        when(loanRepository.countByBorrowerAndStatus(testBorrower, LoanStatus.ACTIVE))
                .thenReturn((long) LoanConstants.MAX_ACTIVE_LOANS);

        // Act & Assert
        assertThatThrownBy(() -> loanApplicationService.apply(applyRequest, testBorrower))
                .isInstanceOf(LoanLimitExceededException.class)
                .hasMessageContaining("Maximum " + LoanConstants.MAX_ACTIVE_LOANS + " active loans allowed");

        verifyNoInteractions(creditBureauService, loanApplicationRepository);
    }

    @Test
    @DisplayName("Should throw Exception when an application is already Pending/Under Review")
    void apply_FailsGuard2_HasPendingApplication() {
        // Arrange
        when(loanRepository.countByBorrowerAndStatus(testBorrower, LoanStatus.ACTIVE)).thenReturn(1L);
        when(loanApplicationRepository.existsByBorrowerAndStatusIn(eq(testBorrower), anyList())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> loanApplicationService.apply(applyRequest, testBorrower))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You already have a loan application under review.");
    }

    @Test
    @DisplayName("Should Auto-Reject when DTI is too high")
    void apply_AutoRejects_WhenDtiIsHigh() {
        // Arrange
        when(loanRepository.countByBorrowerAndStatus(testBorrower, LoanStatus.ACTIVE)).thenReturn(0L);
        when(loanApplicationRepository.existsByBorrowerAndStatusIn(any(), any())).thenReturn(false);

        ExternalDebtResult bureauResult = new ExternalDebtResult(new BigDecimal("20000"), "AVAILABLE");
        when(creditBureauService.fetchExternalEmi("ABCDE1234F")).thenReturn(bureauResult);
        when(loanRepository.sumActiveMonthlyEmi(testBorrower.getId())).thenReturn(Optional.of(BigDecimal.ZERO));

        // Simulating a DTI of 45%
        when(dtiCalculationService.calculateInitialDti(any(), any(), any())).thenReturn(new BigDecimal("45.00"));

        // Service returns null when DTI > 40%
        when(dtiCalculationService.suggestStrategy(any(), anyInt())).thenReturn(null);
        when(loanApplicationRepository.getNextApplicationSequence()).thenReturn(1L);

        LoanApplication savedApp = new LoanApplication();
        savedApp.setId(400L);
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenReturn(savedApp);

        // Act
        loanApplicationService.apply(applyRequest, testBorrower);

        // Assert
        verify(loanApplicationRepository).save(applicationCaptor.capture());
        LoanApplication capturedApp = applicationCaptor.getValue();

        assertThat(capturedApp.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(capturedApp.getRejectionReason()).contains("Auto-rejected");

        // Fix: Be explicit with the types!
        verify(auditService).log(any(AuditRequest.class));
        verify(eventPublisher).publishEvent(any(LoanApplicationSubmittedEvent.class));
    }

    @Test
    @DisplayName("Should successfully submit application and set status to PENDING")
    void apply_Success() {
        // Arrange
        when(loanRepository.countByBorrowerAndStatus(testBorrower, LoanStatus.ACTIVE)).thenReturn(0L);
        when(loanApplicationRepository.existsByBorrowerAndStatusIn(any(), any())).thenReturn(false);

        ExternalDebtResult bureauResult = new ExternalDebtResult(new BigDecimal("5000"), "AVAILABLE");
        when(creditBureauService.fetchExternalEmi("ABCDE1234F")).thenReturn(bureauResult);
        when(loanRepository.sumActiveMonthlyEmi(testBorrower.getId())).thenReturn(Optional.of(BigDecimal.ZERO));

        when(dtiCalculationService.calculateInitialDti(any(), any(), any())).thenReturn(new BigDecimal("10.00"));
        when(dtiCalculationService.suggestStrategy(any(), anyInt())).thenReturn(LoanStrategy.REDUCING_BALANCE_LOAN);
        when(loanApplicationRepository.getNextApplicationSequence()).thenReturn(123L);

        LoanApplication savedApp = new LoanApplication();
        savedApp.setId(500L);
        savedApp.setStatus(ApplicationStatus.PENDING);

        when(loanApplicationRepository.save(any(LoanApplication.class))).thenReturn(savedApp);
        when(loanApplicationMapper.toBorrowerResponse(savedApp)).thenReturn(new BorrowerApplicationResponse());

        // Act
        BorrowerApplicationResponse response = loanApplicationService.apply(applyRequest, testBorrower);

        // Assert
        assertThat(response).isNotNull();
        verify(loanApplicationRepository).save(applicationCaptor.capture());
        LoanApplication capturedApp = applicationCaptor.getValue();

        assertThat(capturedApp.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(capturedApp.getSuggestedStrategy()).isEqualTo(LoanStrategy.REDUCING_BALANCE_LOAN);

        // Fix: Be explicit with the types!
        verify(auditService).log(any(AuditRequest.class));
        verify(eventPublisher).publishEvent(any(LoanApplicationSubmittedEvent.class));
    }

    @Test
    @DisplayName("Should successfully cancel an owned PENDING application")
    void cancelApplication_Success() {
        // Arrange
        LoanApplication application = new LoanApplication();
        application.setId(400L);
        application.setBorrower(testBorrower);
        application.setStatus(ApplicationStatus.PENDING); // Status must be PENDING to pass ValidationUtil

        when(loanApplicationRepository.findByApplicationNumber("APP-123")).thenReturn(Optional.of(application));

        // Act
        loanApplicationService.cancelApplication("APP-123", testBorrower);

        // Assert
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        verify(loanApplicationRepository).save(application);
        verify(auditService).log(any());
    }

    @Test
    @DisplayName("Should throw Unauthorized exception if canceling someone else's application")
    void cancelApplication_Unauthorized() {
        // Arrange
        User anotherUser = new Borrower();
        anotherUser.setId(400L); // Different ID than testBorrower

        LoanApplication application = new LoanApplication();
        application.setBorrower(anotherUser);

        when(loanApplicationRepository.findByApplicationNumber("APP-123")).thenReturn(Optional.of(application));

        // Act & Assert
        assertThatThrownBy(() -> loanApplicationService.cancelApplication("APP-123", testBorrower))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("You can only cancel your own application.");

        verify(loanApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw Not Found exception if application does not exist")
    void cancelApplication_NotFound() {
        // Arrange
        when(loanApplicationRepository.findByApplicationNumber("APP-999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> loanApplicationService.cancelApplication("APP-999", testBorrower))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Application not found");
    }

    @Test
    @DisplayName("Should return list of pending applications for Loan Officer")
    void getPendingApplications_Success() {
        // Arrange
        List<LoanApplication> pendingApps = List.of(new LoanApplication(), new LoanApplication());
        when(loanApplicationRepository.findByStatusOrderByCreatedAtAsc(ApplicationStatus.PENDING)).thenReturn(pendingApps);

        List<OfficerApplicationResponse> responses = List.of(new OfficerApplicationResponse(), new OfficerApplicationResponse());
        when(loanApplicationMapper.toOfficerResponseList(pendingApps)).thenReturn(responses);

        // Act
        List<OfficerApplicationResponse> result = loanApplicationService.getPendingApplications();

        // Assert
        assertThat(result).hasSize(2);
        verify(loanApplicationRepository).findByStatusOrderByCreatedAtAsc(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("Should return list of borrower's own applications")
    void getMyApplications_Success() {
        // Arrange
        List<LoanApplication> myApps = List.of(new LoanApplication());
        when(loanApplicationRepository.findByBorrowerOrderByCreatedAtDesc(testBorrower)).thenReturn(myApps);

        List<BorrowerApplicationResponse> responses = List.of(new BorrowerApplicationResponse());
        when(loanApplicationMapper.toBorrowerResponseList(myApps)).thenReturn(responses);

        // Act
        List<BorrowerApplicationResponse> result = loanApplicationService.getMyApplications(testBorrower);

        // Assert
        assertThat(result).hasSize(1);
        verify(loanApplicationRepository).findByBorrowerOrderByCreatedAtDesc(testBorrower);
    }
}