package com.loanflow.service.impl;

import com.loanflow.dto.request.AuditRequest;
import com.loanflow.dto.request.LoanDecisionRequest;
import com.loanflow.dto.response.LoanResponse;
import com.loanflow.entity.Loan;
import com.loanflow.entity.LoanApplication;
import com.loanflow.entity.user.Borrower;
import com.loanflow.entity.user.User;
import com.loanflow.enums.ApplicationStatus;
import com.loanflow.enums.LoanStatus;
import com.loanflow.enums.LoanStrategy;
import com.loanflow.enums.Role;
import com.loanflow.event.LoanClosedEvent;
import com.loanflow.event.LoanDecisionEvent;
import com.loanflow.exception.BusinessRuleException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.mapper.LoanMapper;
import com.loanflow.repository.EmiScheduleRepository;
import com.loanflow.repository.LoanApplicationRepository;
import com.loanflow.repository.LoanRepository;
import com.loanflow.service.AuditService;
import com.loanflow.service.DtiCalculationService;
import com.loanflow.service.EmiScheduleService;
import com.loanflow.service.LoanStatusTransitionService;
import com.loanflow.strategy.EmiCalculationStrategy;
import com.loanflow.strategy.LoanStrategyFactory;
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
class LoanServiceImplTest {

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private EmiScheduleRepository emiScheduleRepository;
    @Mock private EmiScheduleService emiScheduleService;
    @Mock private DtiCalculationService dtiCalculationService;
    @Mock private LoanStatusTransitionService loanStatusTransitionService;
    @Mock private LoanStrategyFactory loanStrategyFactory;
    @Mock private AuditService auditService;
    @Mock private LoanMapper loanMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private EmiCalculationStrategy emiCalculationStrategy; // Mocked interface for strategy resolution

    @InjectMocks
    private LoanServiceImpl loanService;

    @Captor
    private ArgumentCaptor<Loan> loanCaptor;

    @Captor
    private ArgumentCaptor<LoanApplication> applicationCaptor;

    private User officer;
    private Borrower borrower;
    private LoanApplication application;
    private final UUID appId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        officer = new User();
        officer.setId(UUID.randomUUID());
        officer.setRole(Role.LOAN_OFFICER);

        borrower = new Borrower();
        borrower.setId(UUID.randomUUID());
        borrower.setRole(Role.BORROWER);

        application = new LoanApplication();
        application.setId(appId);
        application.setBorrower(borrower);
        application.setStatus(ApplicationStatus.UNDER_REVIEW); // Valid status for ValidationUtil
        application.setRequestedAmount(new BigDecimal("500000"));
        application.setTenureMonths(24);
        application.setMonthlyIncome(new BigDecimal("80000"));
        application.setExistingMonthlyEmi(new BigDecimal("10000"));
        application.setSuggestedStrategy(LoanStrategy.REDUCING_BALANCE_LOAN);
    }

    @Test
    @DisplayName("Should successfully reject application, save status, audit, and fire event")
    void processDecision_Reject_Success() {
        // Arrange
        LoanDecisionRequest request = new LoanDecisionRequest();
        request.setApproved(false);
        request.setRejectionReason("Credit score too low");

        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(application));

        // Act
        LoanResponse response = loanService.processDecision(appId, request, officer);

        // Assert
        assertThat(response).isNull(); // Rejection returns null

        verify(loanApplicationRepository).save(applicationCaptor.capture());
        LoanApplication savedApp = applicationCaptor.getValue();
        assertThat(savedApp.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(savedApp.getRejectionReason()).isEqualTo("Credit score too low");
        assertThat(savedApp.getReviewedBy()).isEqualTo(officer);

        verify(auditService, times(1)).log(any(AuditRequest.class));
        verify(eventPublisher).publishEvent(any(LoanDecisionEvent.class));
        verifyNoInteractions(loanRepository, emiScheduleService); // Loan generation should not happen
    }

    @Test
    @DisplayName("Should successfully approve application, create loan, generate EMIs, audit, and fire event")
    void processDecision_Approve_Success() {
        // Arrange
        LoanDecisionRequest request = new LoanDecisionRequest();
        request.setApproved(true);
        request.setInterestRatePerAnnum(new BigDecimal("10.5"));
        // Override strategy not provided, should fallback to suggested strategy (REDUCING_BALANCE_LOAN)

        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(loanRepository.sumActiveMonthlyEmi(borrower.getId())).thenReturn(Optional.of(BigDecimal.ZERO));
        when(loanRepository.getNextLoanSequence()).thenReturn(1001L);

        Loan mockSavedLoan = new Loan();
        mockSavedLoan.setId(UUID.randomUUID());
        when(loanRepository.save(any(Loan.class))).thenReturn(mockSavedLoan); // For the initial save

        when(loanStrategyFactory.resolve(LoanStrategy.REDUCING_BALANCE_LOAN)).thenReturn(emiCalculationStrategy);
        when(emiScheduleService.generateSchedule(mockSavedLoan, emiCalculationStrategy)).thenReturn(new BigDecimal("23000"));
        when(dtiCalculationService.calculateFinalDti(any(), any(), any(), any())).thenReturn(new BigDecimal("35.5"));

        LoanResponse expectedResponse = new LoanResponse();
        when(loanMapper.toResponse(mockSavedLoan)).thenReturn(expectedResponse);

        // Act
        LoanResponse response = loanService.processDecision(appId, request, officer);

        // Assert
        assertThat(response).isNotNull();

        // Verify Application Status update
        verify(loanApplicationRepository).save(application);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);

        // Verify Loan Creation details via Captor
        verify(loanRepository, atLeastOnce()).save(loanCaptor.capture());
        Loan createdLoan = loanCaptor.getAllValues().get(0); // The first save before schedule generation

        assertThat(createdLoan.getApprovedAmount()).isEqualByComparingTo("500000");
        assertThat(createdLoan.getInterestRatePerAnnum()).isEqualByComparingTo("10.5");
        assertThat(createdLoan.getStrategy()).isEqualTo(LoanStrategy.REDUCING_BALANCE_LOAN);
        assertThat(createdLoan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(createdLoan.getLoanNumber()).contains("LN-"); // Check sequence prefix format

        // Verify Audits (2 audits: 1 for App Approved, 1 for Loan Created)
        verify(auditService, times(2)).log(any(AuditRequest.class));

        // Verify Event Published
        verify(eventPublisher).publishEvent(any(LoanDecisionEvent.class));
    }

    @Test
    @DisplayName("Should throw BusinessRuleException if approved but no valid strategy exists")
    void processDecision_Approve_ThrowsExceptionWhenNoStrategy() {
        // Arrange
        application.setSuggestedStrategy(null); // No strategy suggested

        LoanDecisionRequest request = new LoanDecisionRequest();
        request.setApproved(true);
        request.setOverrideStrategy(null); // No override provided

        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(application));

        // Act & Assert
        assertThatThrownBy(() -> loanService.processDecision(appId, request, officer))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No valid loan strategy available");

        verifyNoInteractions(loanRepository);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when application does not exist")
    void processDecision_ThrowsNotFound() {
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.processDecision(appId, new LoanDecisionRequest(), officer))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan Application not found");
    }

    @Test
    @DisplayName("Should close loan if all EMIs are paid")
    void closeLoanIfCompleted_WhenZeroUnpaid_ClosesLoan() {
        // Arrange
        Loan loan = new Loan();
        loan.setId(UUID.randomUUID());
        loan.setStatus(LoanStatus.ACTIVE);

        when(emiScheduleRepository.countByLoanAndStatusNot(any(), any())).thenReturn(0L);

        // Act
        loanService.closeLoanIfCompleted(loan);

        // Assert
        verify(loanStatusTransitionService).transition(eq(loan), eq(LoanStatus.CLOSED), isNull(), eq("All EMIs paid"));
        verify(loanRepository).save(loan);
        assertThat(loan.getClosedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(LoanClosedEvent.class));
    }

    @Test
    @DisplayName("Should do nothing if loan still has unpaid EMIs")
    void closeLoanIfCompleted_WhenHasUnpaid_DoesNothing() {
        // Arrange
        Loan loan = new Loan();
        when(emiScheduleRepository.countByLoanAndStatusNot(any(), any())).thenReturn(5L); // 5 unpaid EMIs left

        // Act
        loanService.closeLoanIfCompleted(loan);

        // Assert
        verifyNoInteractions(loanStatusTransitionService, loanRepository, eventPublisher);
    }

    @Test
    @DisplayName("Should return list of mapped loans for a borrower")
    void getMyLoans_Success() {
        // Arrange
        List<Loan> loans = List.of(new Loan(), new Loan());
        when(loanRepository.findByBorrowerOrderByCreatedAtDesc(borrower)).thenReturn(loans);

        List<LoanResponse> mappedResponses = List.of(new LoanResponse(), new LoanResponse());
        when(loanMapper.toResponseList(loans)).thenReturn(mappedResponses);

        // Act
        List<LoanResponse> result = loanService.getMyLoans(borrower);

        // Assert
        assertThat(result).hasSize(2);
        verify(loanRepository).findByBorrowerOrderByCreatedAtDesc(borrower);
    }

    @Test
    @DisplayName("Should return loan when found by ID")
    void findById_Success() {
        // Arrange
        UUID loanId = UUID.randomUUID();
        Loan loan = new Loan();
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

        // Act
        Loan result = loanService.findById(loanId);

        // Assert
        assertThat(result).isEqualTo(loan);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when loan ID is not found")
    void findById_ThrowsNotFound() {
        // Arrange
        UUID loanId = UUID.randomUUID();
        when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> loanService.findById(loanId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan not found: " + loanId);
    }
}