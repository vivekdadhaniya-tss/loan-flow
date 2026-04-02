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
    private final Long emiId = 200L;
    private final String loanNumber = "LN-123456";

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

        testEmi = new EmiSchedule();
        testEmi.setId(emiId);
        testEmi.setLoan(activeLoan);
        testEmi.setStatus(EmiStatus.PENDING);
        testEmi.setTotalEmiAmount(new BigDecimal("15000.00"));
        testEmi.setInstallmentNumber(1);

        request = new PaymentSimulationRequest();
        request.setEmiScheduleId(emiId);
    }

    @Test
    @DisplayName("Should successfully process standard PENDING payment")
    void simulatePayment_PendingEmi_Success() {
        // Arrange
        when(emiScheduleRepository.findById(emiId)).thenReturn(Optional.of(testEmi));
        when(paymentRepository.getNextReceiptSequence()).thenReturn(105L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(new PaymentResponse());

        // Act
        PaymentResponse response = paymentService.simulatePayment(request, borrower);

        // Assert
        assertThat(response).isNotNull();
        assertThat(testEmi.getStatus()).isEqualTo(EmiStatus.PAID);
        assertThat(testEmi.getPaidAt()).isNotNull();

        verify(emiScheduleRepository).save(testEmi);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getPaidAmount()).isEqualByComparingTo("15000.00");
        assertThat(savedPayment.getReceiptNumber()).contains("RCP-"); // Verifying receipt format

        verify(auditService).log(any(AuditRequest.class));
        verify(eventPublisher).publishEvent(any(PaymentReceivedEvent.class));
        verify(loanService).closeLoanIfCompleted(activeLoan);

        // Ensure overdue logic was NOT triggered
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

        when(emiScheduleRepository.findById(emiId)).thenReturn(Optional.of(testEmi));
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

        verify(loanRepository).save(loanCaptor.capture());
        Loan savedLoan = loanCaptor.getValue();
        assertThat(savedLoan.getOverDueCount()).isEqualTo(1); // Decremented from 2 to 1
    }

    @Test
    @DisplayName("Should throw Exception if Receipt Sequence generation fails")
    void simulatePayment_ThrowsException_IfSequenceFails() {
        // Arrange
        when(emiScheduleRepository.findById(emiId)).thenReturn(Optional.of(testEmi));
        when(paymentRepository.getNextReceiptSequence()).thenReturn(null); // Sequence generation fails

        // Act & Assert
        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate receipt number sequence");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException if EMI does not exist")
    void simulatePayment_ThrowsException_IfEmiNotFound() {
        when(emiScheduleRepository.findById(emiId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("EMI Schedule not found");
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