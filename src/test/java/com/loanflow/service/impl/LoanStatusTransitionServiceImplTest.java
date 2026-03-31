package com.loanflow.service.impl;

import com.loanflow.entity.Loan;
import com.loanflow.entity.LoanStatusHistory;
import com.loanflow.entity.user.User;
import com.loanflow.enums.LoanStatus;
import com.loanflow.exception.InvalidStatusTransitionException;
import com.loanflow.repository.LoanStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanStatusTransitionServiceImplTest {

    @Mock
    private LoanStatusHistoryRepository loanStatusHistoryRepository;

    @InjectMocks
    private LoanStatusTransitionServiceImpl transitionService;

    @Captor
    private ArgumentCaptor<LoanStatusHistory> historyCaptor;

    private Loan testLoan;
    private User testUser;

    @BeforeEach
    void setUp() {
        testLoan = new Loan();
        testLoan.setId(UUID.randomUUID());

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("officer@loanflow.com");
    }

    @Test
    @DisplayName("Should allow transition from ACTIVE to CLOSED by System (null user)")
    void transition_ActiveToClosed_BySystem_Success() {
        // Arrange
        testLoan.setStatus(LoanStatus.ACTIVE);
        String reason = "All EMIs paid";

        // Act
        transitionService.transition(testLoan, LoanStatus.CLOSED, null, reason);

        // Assert
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.CLOSED);

        verify(loanStatusHistoryRepository).save(historyCaptor.capture());
        LoanStatusHistory savedHistory = historyCaptor.getValue();

        assertThat(savedHistory.getLoan()).isEqualTo(testLoan);
        assertThat(savedHistory.getOldStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(savedHistory.getNewStatus()).isEqualTo(LoanStatus.CLOSED);
        assertThat(savedHistory.getChangedBy()).isNull(); // System transition
        assertThat(savedHistory.getReason()).isEqualTo(reason);
        assertThat(savedHistory.getChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should allow transition from ACTIVE to DEFAULTED by User")
    void transition_ActiveToDefaulted_ByUser_Success() {
        // Arrange
        testLoan.setStatus(LoanStatus.ACTIVE);
        String reason = "3 consecutive missed EMIs";

        // Act
        transitionService.transition(testLoan, LoanStatus.DEFAULTED, testUser, reason);

        // Assert
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.DEFAULTED);

        verify(loanStatusHistoryRepository).save(historyCaptor.capture());
        LoanStatusHistory savedHistory = historyCaptor.getValue();

        assertThat(savedHistory.getOldStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(savedHistory.getNewStatus()).isEqualTo(LoanStatus.DEFAULTED);
        assertThat(savedHistory.getChangedBy()).isEqualTo(testUser);
        assertThat(savedHistory.getReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("Should allow transition from DEFAULTED to ACTIVE (e.g. Borrower clears dues)")
    void transition_DefaultedToActive_Success() {
        // Arrange
        testLoan.setStatus(LoanStatus.DEFAULTED);

        // Act
        transitionService.transition(testLoan, LoanStatus.ACTIVE, testUser, "Overdue cleared");

        // Assert
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        verify(loanStatusHistoryRepository, times(1)).save(any(LoanStatusHistory.class));
    }

    @Test
    @DisplayName("Should allow transition from DEFAULTED to WRITTEN_OFF")
    void transition_DefaultedToWrittenOff_Success() {
        // Arrange
        testLoan.setStatus(LoanStatus.DEFAULTED);

        // Act
        transitionService.transition(testLoan, LoanStatus.WRITTEN_OFF, testUser, "Unrecoverable debt");

        // Assert
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.WRITTEN_OFF);
        verify(loanStatusHistoryRepository, times(1)).save(any(LoanStatusHistory.class));
    }

    @Test
    @DisplayName("Should throw Exception when attempting an invalid transition (ACTIVE -> WRITTEN_OFF)")
    void transition_ThrowsException_OnInvalidTransition_ActiveToWrittenOff() {
        // Arrange
        testLoan.setStatus(LoanStatus.ACTIVE); // Active loans cannot be written off directly

        // Act & Assert
        assertThatThrownBy(() -> transitionService.transition(testLoan, LoanStatus.WRITTEN_OFF, testUser, "Skip default"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot transition Loan from ACTIVE to WRITTEN_OFF");

        // Verify history was never saved
        verifyNoInteractions(loanStatusHistoryRepository);
        // Verify loan status was NOT changed
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw Exception when attempting to transition a terminal state (CLOSED -> ACTIVE)")
    void transition_ThrowsException_OnTerminalState_ClosedToActive() {
        // Arrange
        testLoan.setStatus(LoanStatus.CLOSED); // Closed is terminal, allowed set is empty

        // Act & Assert
        assertThatThrownBy(() -> transitionService.transition(testLoan, LoanStatus.ACTIVE, testUser, "Reopen loan"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot transition Loan from CLOSED to ACTIVE");

        verifyNoInteractions(loanStatusHistoryRepository);
    }

    @Test
    @DisplayName("Should throw Exception for self-transition (ACTIVE -> ACTIVE)")
    void transition_ThrowsException_OnSelfTransition() {
        // Arrange
        testLoan.setStatus(LoanStatus.ACTIVE);

        // Act & Assert
        assertThatThrownBy(() -> transitionService.transition(testLoan, LoanStatus.ACTIVE, null, "Refresh"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot transition Loan from ACTIVE to ACTIVE");

        verifyNoInteractions(loanStatusHistoryRepository);
    }
}