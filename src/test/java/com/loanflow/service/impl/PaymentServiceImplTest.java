package com.loanflow.service.impl;

import com.loanflow.dto.request.AuditRequest;
import com.loanflow.dto.request.PaymentSimulationRequest;
import com.loanflow.dto.response.PaymentResponse;
import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;
import com.loanflow.entity.OverdueTracker;
import com.loanflow.entity.Payment;
import com.loanflow.entity.user.Borrower;
import com.loanflow.entity.user.User;
import com.loanflow.enums.EmiStatus;
import com.loanflow.enums.LoanStatus;
import com.loanflow.enums.PenaltyStatus;
import com.loanflow.enums.Role;
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
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private EmiScheduleRepository emiScheduleRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OverdueTrackerRepository overdueTrackerRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private LoanService loanService;
    @Mock private AuditService auditService;
    @Mock private PaymentMapper paymentMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Captor private ArgumentCaptor<Payment> paymentCaptor;
    @Captor private ArgumentCaptor<OverdueTracker> trackerCaptor;
    @Captor private ArgumentCaptor<Loan> loanCaptor;

    private User borrower;
    private Loan activeLoan;
    private EmiSchedule testEmi;
    private PaymentSimulationRequest request;
    private final String loanNumber = "LN-20260402-001009";

    @BeforeEach
    void setUp() {
        borrower = new Borrower();
        borrower.setId(100L);
        borrower.setRole(Role.BORROWER);

        activeLoan = new Loan();
        activeLoan.setId(200L);
        activeLoan.setBorrower(borrower);
        activeLoan.setStatus(LoanStatus.ACTIVE);
        activeLoan.setOverDueCount(0);
        activeLoan.setLoanNumber(loanNumber);
        // FIX: Must set Outstanding Principal for the new deduction logic!
        activeLoan.setOutstandingPrincipal(new BigDecimal("50000.00"));

        testEmi = new EmiSchedule();
        testEmi.setId(500L);
        testEmi.setLoan(activeLoan);
        testEmi.setStatus(EmiStatus.PENDING);
        testEmi.setTotalEmiAmount(new BigDecimal("15000.00"));
        // FIX: Must set Principal Amount for the new deduction logic!
        testEmi.setPrincipalAmount(new BigDecimal("10000.00"));
        testEmi.setInstallmentNumber(1);

        request = new PaymentSimulationRequest();
        request.setLoanNumber(loanNumber);
        request.setInstallmentCount(1);
    }

    @Test
    @DisplayName("Should successfully process standard PENDING payment and reduce principal")
    void simulatePayment_PendingEmi_Success() {
        // Arrange
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(PageRequest.class)))
                .thenReturn(List.of(testEmi));

        when(paymentRepository.getNextReceiptSequence()).thenReturn(105L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(new PaymentResponse());

        // Act
        List<PaymentResponse> response = paymentService.simulatePayment(request, borrower);

        // Assert
        assertThat(response).isNotNull().hasSize(1);
        assertThat(testEmi.getStatus()).isEqualTo(EmiStatus.PAID);
        assertThat(testEmi.getPaidAt()).isNotNull();

        verify(emiScheduleRepository).save(testEmi);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getPaidAmount()).isEqualByComparingTo("15000.00");
        assertThat(savedPayment.getReceiptNumber()).contains("RCP-");

        // Verify Principal was deducted (50,000 - 10,000)
        verify(loanRepository).save(loanCaptor.capture());
        Loan savedLoan = loanCaptor.getValue();
        assertThat(savedLoan.getOutstandingPrincipal()).isEqualByComparingTo("40000.00");

        verify(auditService).log(any(AuditRequest.class));
        verify(eventPublisher).publishEvent(any(PaymentReceivedEvent.class));
        verify(loanService).closeLoanIfCompleted(activeLoan);
        verifyNoInteractions(overdueTrackerRepository);
    }

    @Test
    @DisplayName("Should successfully process OVERDUE payment and resolve tracker")
    void simulatePayment_OverdueEmi_ResolvesTrackerAndDecrementsCount() {
        // Arrange
        testEmi.setStatus(EmiStatus.OVERDUE);
        activeLoan.setOverDueCount(2); // Loan has 2 overdue EMIs

        OverdueTracker tracker = new OverdueTracker();
        tracker.setEmiSchedule(testEmi);

        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(PageRequest.class)))
                .thenReturn(List.of(testEmi));

        when(paymentRepository.getNextReceiptSequence()).thenReturn(106L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(overdueTrackerRepository.findByEmiSchedule(testEmi)).thenReturn(Optional.of(tracker));

        // Act
        paymentService.simulatePayment(request, borrower);

        // Assert
        verify(overdueTrackerRepository).save(trackerCaptor.capture());
        OverdueTracker savedTracker = trackerCaptor.getValue();
        assertThat(savedTracker.getResolvedAt()).isNotNull();
        assertThat(savedTracker.getPenaltyStatus()).isEqualTo(PenaltyStatus.SETTLED);

        // Capture loan update (contains both overdue count update AND principal deduction)
        verify(loanRepository, atLeastOnce()).save(loanCaptor.capture());
        Loan savedLoan = loanCaptor.getValue();
        assertThat(savedLoan.getOverDueCount()).isEqualTo(1); // Decremented from 2 to 1
    }

    @Test
    @DisplayName("Should throw Exception if user tries to pay more than max allowed EMIs")
    void simulatePayment_ThrowsException_IfExceedsMaxAllowed() {
        // Arrange
        request.setInstallmentCount(5); // Requesting 5

        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);

        // Mock returning 10 pending EMIs
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(PageRequest.class)))
                .thenReturn(List.of(testEmi, testEmi, testEmi, testEmi, testEmi));
        // Since none are OVERDUE, overdueCount = 0. maxAllowed = 0 + 3 = 3.

        // Act & Assert
        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("You can only pay up to 3 installments at a time");
    }

    @Test
    @DisplayName("Should throw Exception if Loan does not exist")
    void simulatePayment_ThrowsException_IfLoanNotFound() {
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan not found: " + loanNumber);
    }

    @Test
    @DisplayName("Should throw Exception if there are no pending EMIs")
    void simulatePayment_ThrowsException_IfNoPendingEmis() {
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No pending EMIs found for this loan.");
    }

    @Test
    @DisplayName("Should throw Exception if Receipt Sequence generation fails")
    void simulatePayment_ThrowsException_IfSequenceFails() {
        // Arrange
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(PageRequest.class)))
                .thenReturn(List.of(testEmi));

        when(paymentRepository.getNextReceiptSequence()).thenReturn(null); // Sequence generation fails

        // Act & Assert
        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate receipt number sequence");
    }

    // --- GET PAYMENTS BY LOAN NUMBER TESTS ---

    @Test
    @DisplayName("Officer should be able to view payments for any loan")
    void getPaymentsByLoanNumber_OfficerAccess_Success() {
        // Arrange
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.hasRole("LOAN_OFFICER")).thenReturn(true); // Is an officer

        List<Payment> payments = List.of(new Payment(), new Payment());
        when(paymentRepository.findByLoan_LoanNumberOrderByPaidAtDesc(loanNumber)).thenReturn(payments);

        // Act
        List<PaymentResponse> result = paymentService.getPaymentsByLoanNumber(loanNumber);

        // Assert
        assertThat(result).hasSize(2);
        verify(securityUtils, never()).isOwner(any()); // Owner check bypassed
    }

    @Test
    @DisplayName("Borrower should be able to view their OWN payments")
    void getPaymentsByLoanNumber_OwnerAccess_Success() {
        // Arrange
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.hasRole("LOAN_OFFICER")).thenReturn(false);
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true); // Is the owner!

        List<Payment> payments = List.of(new Payment());
        when(paymentRepository.findByLoan_LoanNumberOrderByPaidAtDesc(loanNumber)).thenReturn(payments);

        // Act
        List<PaymentResponse> result = paymentService.getPaymentsByLoanNumber(loanNumber);

        // Assert
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should throw UnauthorizedAccessException if Borrower tries to view someone else's payments")
    void getPaymentsByLoanNumber_ThrowsException_IfUnauthorized() {
        // Arrange
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.hasRole("LOAN_OFFICER")).thenReturn(false);
        when(securityUtils.isOwner(borrower.getId())).thenReturn(false); // Trying to view another user's loan!

        // Act & Assert
        assertThatThrownBy(() -> paymentService.getPaymentsByLoanNumber(loanNumber))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Access Denied");

        verifyNoInteractions(paymentRepository); // Ensure data is not fetched
    }
}