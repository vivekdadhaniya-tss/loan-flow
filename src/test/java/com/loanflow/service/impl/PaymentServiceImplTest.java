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
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
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
    private EmiSchedule testEmi1;
    private EmiSchedule testEmi2;
    private PaymentSimulationRequest request;
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

        testEmi1 = new EmiSchedule();
        testEmi1.setId(1L);
        testEmi1.setLoan(activeLoan);
        testEmi1.setStatus(EmiStatus.PENDING);
        testEmi1.setTotalEmiAmount(new BigDecimal("15000.00"));
        testEmi1.setInstallmentNumber(1);

        testEmi2 = new EmiSchedule();
        testEmi2.setId(2L);
        testEmi2.setLoan(activeLoan);
        testEmi2.setStatus(EmiStatus.PENDING);
        testEmi2.setTotalEmiAmount(new BigDecimal("15000.00"));
        testEmi2.setInstallmentNumber(2);

        request = new PaymentSimulationRequest();
        request.setLoanNumber(loanNumber);
        request.setInstallmentCount(1); // Default to 1 for basic tests
    }

    // =========================================================================
    // SIMULATE PAYMENT TESTS
    // =========================================================================

    @Test
    @DisplayName("Should successfully process a single standard PENDING payment")
    void simulatePayment_SinglePendingEmi_Success() {
        // Arrange
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(Pageable.class)))
                .thenReturn(List.of(testEmi1));

        when(paymentRepository.getNextReceiptSequence()).thenReturn(105L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(new PaymentResponse());

        // Act
        List<PaymentResponse> response = paymentService.simulatePayment(request, borrower);

        // Assert
        assertThat(response).hasSize(1);
        assertThat(testEmi1.getStatus()).isEqualTo(EmiStatus.PAID);
        assertThat(testEmi1.getPaidAt()).isNotNull();

        verify(emiScheduleRepository).save(testEmi1);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getPaidAmount()).isEqualByComparingTo("15000.00");
        assertThat(savedPayment.getReceiptNumber()).contains("RCP-");

        verify(auditService).log(any(AuditRequest.class));
        verify(eventPublisher).publishEvent(any(PaymentReceivedEvent.class));
        verify(loanService).closeLoanIfCompleted(activeLoan);
    }

    @Test
    @DisplayName("Should successfully process multiple installments at once")
    void simulatePayment_MultipleInstallments_Success() {
        // Arrange
        request.setInstallmentCount(2); // Requesting to pay 2 EMIs

        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(Pageable.class)))
                .thenReturn(List.of(testEmi1, testEmi2));

        when(paymentRepository.getNextReceiptSequence()).thenReturn(105L, 106L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(new PaymentResponse());

        // Act
        List<PaymentResponse> response = paymentService.simulatePayment(request, borrower);

        // Assert
        assertThat(response).hasSize(2);
        assertThat(testEmi1.getStatus()).isEqualTo(EmiStatus.PAID);
        assertThat(testEmi2.getStatus()).isEqualTo(EmiStatus.PAID);
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should successfully process OVERDUE payment and resolve tracker")
    void simulatePayment_OverdueEmi_ResolvesTrackerAndDecrementsCount() {
        // Arrange
        testEmi1.setStatus(EmiStatus.OVERDUE);
        activeLoan.setOverDueCount(2);

        OverdueTracker tracker = new OverdueTracker();
        tracker.setEmiSchedule(testEmi1);

        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(Pageable.class)))
                .thenReturn(List.of(testEmi1));

        when(paymentRepository.getNextReceiptSequence()).thenReturn(106L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(overdueTrackerRepository.findByEmiSchedule(testEmi1)).thenReturn(Optional.of(tracker));

        // Act
        paymentService.simulatePayment(request, borrower);

        // Assert
        verify(overdueTrackerRepository).save(trackerCaptor.capture());
        OverdueTracker savedTracker = trackerCaptor.getValue();
        assertThat(savedTracker.getResolvedAt()).isNotNull();
        assertThat(savedTracker.getPenaltyStatus()).isEqualTo(PenaltyStatus.SETTLED);

        verify(loanRepository).save(loanCaptor.capture());
        Loan savedLoan = loanCaptor.getValue();
        assertThat(savedLoan.getOverDueCount()).isEqualTo(1); // Decremented from 2
    }

    @Test
    @DisplayName("Should throw Exception if user is not authorized to pay this loan")
    void simulatePayment_ThrowsException_IfUnauthorized() {
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(false);
        when(securityUtils.hasRole("ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("You are not authorized to make a payment");
    }

    @Test
    @DisplayName("Should throw Exception if no pending EMIs are found")
    void simulatePayment_ThrowsException_IfNoPendingEmis() {
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(Pageable.class)))
                .thenReturn(List.of()); // Empty list

        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No pending EMIs found");
    }

    @Test
    @DisplayName("Should throw Exception if requested installments exceed max allowed")
    void simulatePayment_ThrowsException_IfRequestedExceedsMaxAllowed() {
        // Arrange
        request.setInstallmentCount(5); // Requesting 5
        // List contains 4 PENDING EMIs (0 overdue). Max allowed should be 0 + 3 = 3.
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);
        when(emiScheduleRepository.findNextUnpaidEmis(eq(activeLoan), any(Pageable.class)))
                .thenReturn(List.of(testEmi1, testEmi2, new EmiSchedule(), new EmiSchedule()));

        // Act & Assert
        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("You can only pay up to 3 installments at a time");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException if Loan does not exist")
    void simulatePayment_ThrowsException_IfLoanNotFound() {
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.simulatePayment(request, borrower))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan not found");
    }


    // =========================================================================
    // GET PAYMENTS BY LOAN NUMBER TESTS
    // =========================================================================

    @Test
    @DisplayName("Officer should be able to view payments for any loan")
    void getPaymentsByLoanNumber_OfficerAccess_Success() {
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.hasRole("LOAN_OFFICER")).thenReturn(true);

        List<Payment> payments = List.of(new Payment(), new Payment());
        when(paymentRepository.findByLoan_LoanNumberOrderByPaidAtDesc(loanNumber)).thenReturn(payments);

        List<PaymentResponse> result = paymentService.getPaymentsByLoanNumber(loanNumber);

        assertThat(result).hasSize(2);
        verify(securityUtils, never()).isOwner(any());
    }

    @Test
    @DisplayName("Borrower should be able to view their OWN payments")
    void getPaymentsByLoanNumber_OwnerAccess_Success() {
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.hasRole("LOAN_OFFICER")).thenReturn(false);
        when(securityUtils.isOwner(borrower.getId())).thenReturn(true);

        List<Payment> payments = List.of(new Payment());
        when(paymentRepository.findByLoan_LoanNumberOrderByPaidAtDesc(loanNumber)).thenReturn(payments);

        List<PaymentResponse> result = paymentService.getPaymentsByLoanNumber(loanNumber);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should throw UnauthorizedAccessException if Borrower tries to view someone else's payments")
    void getPaymentsByLoanNumber_ThrowsException_IfUnauthorized() {
        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(activeLoan));
        when(securityUtils.hasRole("LOAN_OFFICER")).thenReturn(false);
        when(securityUtils.isOwner(borrower.getId())).thenReturn(false);

        assertThatThrownBy(() -> paymentService.getPaymentsByLoanNumber(loanNumber))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Access Denied");

        verifyNoInteractions(paymentRepository);
    }
}