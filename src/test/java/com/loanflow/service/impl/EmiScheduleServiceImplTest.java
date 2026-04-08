package com.loanflow.service.impl;

import com.loanflow.dto.response.EmiScheduleResponse;
import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.mapper.EmiScheduleMapper;
import com.loanflow.repository.EmiScheduleRepository;
import com.loanflow.repository.LoanRepository;
import com.loanflow.strategy.EmiCalculationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmiScheduleServiceImplTest {

    @Mock
    private EmiScheduleRepository emiScheduleRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private EmiScheduleMapper emiScheduleMapper;

    @Mock
    private EmiCalculationStrategy strategy; // Mocking the strategy interface

    @InjectMocks
    private EmiScheduleServiceImpl emiScheduleService;

    private Loan testLoan;

    // FIX: Changed from UUID to Long to match updated Loan entity
    private final Long loanId = 100L;
    private final String loanNumber = "LN-123456";

    @BeforeEach
    void setUp() {
        testLoan = new Loan();
        testLoan.setId(loanId);
        testLoan.setLoanNumber(loanNumber);
    }

    @Test
    @DisplayName("Should generate schedule, save it, and return first installment's total EMI")
    void generateSchedule_Success() {
        // Arrange
        EmiSchedule month1 = new EmiSchedule();
        month1.setTotalEmiAmount(new BigDecimal("1500.50"));

        EmiSchedule month2 = new EmiSchedule();
        month2.setTotalEmiAmount(new BigDecimal("1500.50"));

        List<EmiSchedule> mockSchedule = List.of(month1, month2);

        // When the strategy is called, return our mock list
        when(strategy.generateEmiSchedule(testLoan)).thenReturn(mockSchedule);

        // Act
        BigDecimal baseEmi = emiScheduleService.generateSchedule(testLoan, strategy);

        // Assert
        assertThat(baseEmi).isEqualByComparingTo("1500.50");
        verify(strategy, times(1)).generateEmiSchedule(testLoan);
        verify(emiScheduleRepository, times(1)).saveAll(mockSchedule);
    }

    @Test
    @DisplayName("Should return schedule list when fetched by valid Loan ID")
    void getScheduleByLoan_Success() {
        // Arrange
        List<EmiSchedule> dbSchedule = List.of(new EmiSchedule(), new EmiSchedule());
        List<EmiScheduleResponse> mappedResponse = List.of(new EmiScheduleResponse(), new EmiScheduleResponse());

        when(loanRepository.findById(loanId)).thenReturn(Optional.of(testLoan));
        when(emiScheduleRepository.findByLoanOrderByInstallmentNumberAsc(testLoan)).thenReturn(dbSchedule);
        when(emiScheduleMapper.toResponseList(dbSchedule)).thenReturn(mappedResponse);

        // Act
        List<EmiScheduleResponse> result = emiScheduleService.getScheduleByLoan(loanId);

        // Assert
        assertThat(result).hasSize(2);
        verify(loanRepository).findById(loanId);
        verify(emiScheduleRepository).findByLoanOrderByInstallmentNumberAsc(testLoan);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when Loan ID does not exist")
    void getScheduleByLoan_ThrowsNotFoundException() {
        // Arrange
        when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> emiScheduleService.getScheduleByLoan(loanId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan not found: " + loanId);

        // Verify that the downstream repository was NEVER called because the exception stopped execution
        verify(emiScheduleRepository, never()).findByLoanOrderByInstallmentNumberAsc(any());
    }

    @Test
    @DisplayName("Should return schedule list when fetched by valid Loan Number")
    void getScheduleByLoanNumber_Success() {
        // Arrange
        List<EmiSchedule> dbSchedule = List.of(new EmiSchedule());
        List<EmiScheduleResponse> mappedResponse = List.of(new EmiScheduleResponse());

        when(loanRepository.findByLoanNumber(loanNumber)).thenReturn(Optional.of(testLoan));
        when(emiScheduleRepository.findByLoanOrderByInstallmentNumberAsc(testLoan)).thenReturn(dbSchedule);
        when(emiScheduleMapper.toResponseList(dbSchedule)).thenReturn(mappedResponse);

        // Act
        Page<EmiScheduleResponse> result = emiScheduleService.getScheduleByLoanNumber(loanNumber , 0  ,10);

        // Assert
        assertThat(result).hasSize(1);
        verify(loanRepository).findByLoanNumber(loanNumber);
        verify(emiScheduleRepository).findByLoanOrderByInstallmentNumberAsc(testLoan);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when Loan Number does not exist")
    void getScheduleByLoanNumber_ThrowsNotFoundException() {
        // Arrange
        when(loanRepository.findByLoanNumber("INVALID-123")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> emiScheduleService.getScheduleByLoanNumber("INVALID-123" , 0, 10))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan not found: INVALID-123");

        // Verify downstream processes were halted
        verify(emiScheduleRepository, never()).findByLoanOrderByInstallmentNumberAsc(any());
    }
}