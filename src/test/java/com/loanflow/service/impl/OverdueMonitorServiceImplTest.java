package com.loanflow.service.impl;

import com.loanflow.constants.LoanConstants;
import com.loanflow.dto.request.AuditRequest;
import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;
import com.loanflow.entity.OverdueTracker;
import com.loanflow.entity.user.Borrower;
import com.loanflow.enums.EmiStatus;
import com.loanflow.enums.LoanStatus;
import com.loanflow.enums.PenaltyStatus;
import com.loanflow.event.OverdueAlertEvent;
import com.loanflow.repository.EmiScheduleRepository;
import com.loanflow.repository.LoanRepository;
import com.loanflow.repository.OverdueTrackerRepository;
import com.loanflow.service.AuditService;
import com.loanflow.service.LoanStatusTransitionService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverdueMonitorServiceImplTest {

    @Mock private EmiScheduleRepository emiScheduleRepository;
    @Mock private OverdueTrackerRepository overdueTrackerRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private LoanStatusTransitionService loanStatusTransitionService;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OverdueMonitorServiceImpl overdueMonitorService;

    @Captor private ArgumentCaptor<OverdueTracker> trackerCaptor;
    @Captor private ArgumentCaptor<Loan> loanCaptor;
    @Captor private ArgumentCaptor<EmiSchedule> emiCaptor;

    private Loan activeLoan;
    private EmiSchedule testEmi;

    @BeforeEach
    void setUp() {
        Borrower borrower = new Borrower();
        borrower.setId(UUID.randomUUID());

        activeLoan = new Loan();
        activeLoan.setId(UUID.randomUUID());
        activeLoan.setBorrower(borrower);
        activeLoan.setStatus(LoanStatus.ACTIVE);
        activeLoan.setOverDueCount(0);
        activeLoan.setLoanNumber("LN-12345");

        testEmi = new EmiSchedule();
        testEmi.setId(UUID.randomUUID());
        testEmi.setLoan(activeLoan);
        testEmi.setStatus(EmiStatus.PENDING);
        testEmi.setTotalEmiAmount(new BigDecimal("15000.00")); // Updated field for penalty calculation
        testEmi.setInstallmentNumber(1);
    }

//    @Test
//    @DisplayName("Phase 1 - Day 1 Overdue: Finds newly missed PENDING EMIs, applies flat fee, sends alert")
//    void scanAndMarkOverdue_NewOverdue_AppliesFlatFee_NoDailyCharge() {
//        // Arrange
//        testEmi.setDueDate(LocalDate.now().minusDays(1)); // 1 day overdue
//
//        // Mock Phase 1: newly PENDING
//        when(emiScheduleRepository.findByStatusAndDueDateBefore(eq(EmiStatus.PENDING), any(LocalDate.class)))
//                .thenReturn(List.of(testEmi));
//        when(overdueTrackerRepository.findByEmiSchedule(testEmi)).thenReturn(Optional.empty());
//
//        // Mock Phase 2: no unresolved existing trackers
//        when(overdueTrackerRepository.findByResolvedAtIsNull()).thenReturn(Collections.emptyList());
//
//        // Act
//        overdueMonitorService.scanAndMarkOverdue();
//
//        // Assert - EMI Status
//        verify(emiScheduleRepository).save(emiCaptor.capture());
//        assertThat(emiCaptor.getValue().getStatus()).isEqualTo(EmiStatus.OVERDUE);
//
//        // Assert - Tracker updates (Stage 1 & 2 Penalty)
//        verify(overdueTrackerRepository).save(trackerCaptor.capture());
//        OverdueTracker savedTracker = trackerCaptor.getValue();
//
//        assertThat(savedTracker.getDaysOverdue()).isEqualTo(1);
//        assertThat(savedTracker.getDetectedAt()).isNotNull();
//        assertThat(savedTracker.getFixedPenaltyAmount()).isEqualTo(LoanConstants.LATE_FEE_FLAT_AMOUNT);
//        assertThat(savedTracker.getPenaltyCharge()).isEqualTo(BigDecimal.ZERO); // Grace period
//        assertThat(savedTracker.getPenaltyStatus()).isEqualTo(PenaltyStatus.APPLIED);
//        assertThat(savedTracker.getAlertCount()).isEqualTo(1);
//
//        // Assert - Loan Overdue Count Increment
//        verify(loanRepository).save(loanCaptor.capture());
//        assertThat(loanCaptor.getValue().getOverDueCount()).isEqualTo(1);
//
//        // Assert - Events & Audits
//        verify(eventPublisher).publishEvent(any(OverdueAlertEvent.class));
//        verify(auditService).log(any(AuditRequest.class));
//    }

    @Test
    @DisplayName("Phase 2 - Stage 3 Penalty: Updates unresolved tracker past grace period with daily charge")
    void scanAndMarkOverdue_UnresolvedTracker_PastGracePeriod_AppliesDailyPenalty() {
        // Arrange
        int daysOverdue = LoanConstants.PENALTY_GRACE_DAYS + 5;
        testEmi.setDueDate(LocalDate.now().minusDays(daysOverdue));

        OverdueTracker existingTracker = new OverdueTracker();
        existingTracker.setEmiSchedule(testEmi);
        existingTracker.setLoan(activeLoan);
        existingTracker.setDueDate(testEmi.getDueDate());
        existingTracker.setDetectedAt(LocalDateTime.now().minusDays(daysOverdue));
        existingTracker.setFixedPenaltyAmount(LoanConstants.LATE_FEE_FLAT_AMOUNT);
        existingTracker.setPenaltyRate(LoanConstants.OVERDUE_DAILY_PENALTY_RATE);
        existingTracker.setAlertCount(1); // Alert already sent on Day 1

        // Mock Phase 1: No new PENDING EMIs
        when(emiScheduleRepository.findByStatusAndDueDateBefore(any(), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        // Mock Phase 2: Found our existing unresolved tracker
        when(overdueTrackerRepository.findByResolvedAtIsNull()).thenReturn(List.of(existingTracker));

        // Act
        overdueMonitorService.scanAndMarkOverdue();

        // Assert
        verify(overdueTrackerRepository).save(trackerCaptor.capture());
        OverdueTracker savedTracker = trackerCaptor.getValue();

        assertThat(savedTracker.getDaysOverdue()).isEqualTo(daysOverdue);

        // Ensure daily penalty was calculated (rate * days * TOTAL EMI AMOUNT)
        assertThat(savedTracker.getPenaltyCharge()).isGreaterThan(BigDecimal.ZERO);

        // Verify no spam alerts
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Phase 2 - Transitions loan to DEFAULTED when unresolved tracker exceeds threshold days")
    void scanAndMarkOverdue_UnresolvedTracker_TransitionsToDefaulted() {
        // Arrange
        int thresholdDays = LoanConstants.DEFAULT_THRESHOLD_DAYS + 2;
        testEmi.setDueDate(LocalDate.now().minusDays(thresholdDays));

        OverdueTracker existingTracker = new OverdueTracker();
        existingTracker.setEmiSchedule(testEmi);
        existingTracker.setLoan(activeLoan);
        existingTracker.setDueDate(testEmi.getDueDate());
        existingTracker.setDetectedAt(LocalDateTime.now().minusDays(thresholdDays));
        existingTracker.setFixedPenaltyAmount(LoanConstants.LATE_FEE_FLAT_AMOUNT);
        existingTracker.setAlertCount(1);

        when(emiScheduleRepository.findByStatusAndDueDateBefore(any(), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(overdueTrackerRepository.findByResolvedAtIsNull()).thenReturn(List.of(existingTracker));

        // Act
        overdueMonitorService.scanAndMarkOverdue();

        // Assert
        verify(loanStatusTransitionService).transition(
                eq(activeLoan),
                eq(LoanStatus.DEFAULTED),
                isNull(),
                contains("Loan defaulted after")
        );
        verify(loanRepository, atLeastOnce()).save(activeLoan);
    }

    @Test
    @DisplayName("Should successfully scan and mark old DEFAULTED loans as WRITTEN_OFF")
    void scanAndMarkWrittenOff_Success() {
        // Arrange
        Loan defaultedLoan = new Loan();
        defaultedLoan.setId(UUID.randomUUID());
        defaultedLoan.setStatus(LoanStatus.DEFAULTED);

        when(loanRepository.findByStatusAndUpdatedAtBefore(eq(LoanStatus.DEFAULTED), any(LocalDateTime.class)))
                .thenReturn(List.of(defaultedLoan));

        // Act
        overdueMonitorService.scanAndMarkWrittenOff();

        // Assert
        verify(loanStatusTransitionService).transition(
                eq(defaultedLoan),
                eq(LoanStatus.WRITTEN_OFF),
                isNull(),
                contains("Loan written off after")
        );
        verify(loanRepository).save(defaultedLoan);
        verify(auditService).log(any(AuditRequest.class));
    }

    @Test
    @DisplayName("Should do nothing when there are no new overdue EMIs and no unresolved trackers")
    void scanAndMarkOverdue_NoResults_DoesNothing() {
        // Arrange
        when(emiScheduleRepository.findByStatusAndDueDateBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(overdueTrackerRepository.findByResolvedAtIsNull())
                .thenReturn(Collections.emptyList());

        // Act
        overdueMonitorService.scanAndMarkOverdue();

        // Assert
        verifyNoInteractions(loanStatusTransitionService, eventPublisher, auditService);
        verify(overdueTrackerRepository, never()).save(any());
        verify(loanRepository, never()).save(any());
    }
}