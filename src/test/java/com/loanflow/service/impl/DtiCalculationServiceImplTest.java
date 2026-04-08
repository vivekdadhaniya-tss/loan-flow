package com.loanflow.service.impl;

import com.loanflow.constants.LoanConstants;
import com.loanflow.enums.LoanStrategy;
import com.loanflow.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DtiCalculationServiceImplTest {

    private DtiCalculationServiceImpl dtiService;

    @BeforeEach
    void setUp() {
        // No @Mock needed...We test the real, pure logic.
        dtiService = new DtiCalculationServiceImpl();
    }

    @Test
    @DisplayName("Should correctly calculate Initial DTI percentage")
    void calculateInitialDti_Success() {
        // Arrange
        BigDecimal internalEmi = new BigDecimal("1000.00");
        BigDecimal externalEmi = new BigDecimal("2000.00");
        BigDecimal monthlyIncome = new BigDecimal("10000.00");

        // Act
        // Total EMI = 3000. Income = 10000. DTI should be 30.00%
        BigDecimal dti = dtiService.calculateInitialDti(internalEmi, externalEmi, monthlyIncome);

        // Assert
        assertThat(dti).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("Should correctly calculate Final DTI percentage including new loan")
    void calculateFinalDti_Success() {
        // Arrange
        BigDecimal internalEmi = new BigDecimal("1000.00");
        BigDecimal externalEmi = new BigDecimal("2000.00");
        BigDecimal newLoanEmi = new BigDecimal("1500.00");
        BigDecimal monthlyIncome = new BigDecimal("10000.00");

        // Act
        // Total EMI = 4500. Income = 10000. DTI should be 45.00%
        BigDecimal dti = dtiService.calculateFinalDti(internalEmi, externalEmi, newLoanEmi, monthlyIncome);

        // Assert
        assertThat(dti).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("Should suggest FLAT_RATE_LOAN for very low DTI")
    void suggestStrategy_FlatRate_WhenDtiIsLow() {
        // Arrange: Dynamically subtract 1 from your constant to guarantee it is "low"
        BigDecimal lowDti = LoanConstants.DTI_LOW_THRESHOLD.subtract(BigDecimal.ONE);

        // Act
        LoanStrategy strategy = dtiService.suggestStrategy(lowDti, 12);

        // Assert
        assertThat(strategy).isEqualTo(LoanStrategy.FLAT_RATE_LOAN);
    }

    @Test
    @DisplayName("Should suggest REDUCING_BALANCE for mid DTI and short tenure")
    void suggestStrategy_ReducingBalance_WhenDtiIsMid_AndTenureIsShort() {
        // Arrange: Exact MID threshold, and tenure 1 month below the step-up limit
        BigDecimal midDti = LoanConstants.DTI_MID_THRESHOLD;
        int shortTenure = LoanConstants.STEP_UP_TENURE_THRESHOLD - 1;

        // Act
        LoanStrategy strategy = dtiService.suggestStrategy(midDti, shortTenure);

        // Assert
        assertThat(strategy).isEqualTo(LoanStrategy.REDUCING_BALANCE_LOAN);
    }

    @Test
    @DisplayName("Should suggest STEP_UP_EMI for mid DTI and long tenure")
    void suggestStrategy_StepUp_WhenDtiIsMid_AndTenureIsLong() {
        // Arrange: Exact MID threshold, and tenure exactly at the step-up limit
        BigDecimal midDti = LoanConstants.DTI_MID_THRESHOLD;
        int longTenure = LoanConstants.STEP_UP_TENURE_THRESHOLD;

        // Act
        LoanStrategy strategy = dtiService.suggestStrategy(midDti, longTenure);

        // Assert
        assertThat(strategy).isEqualTo(LoanStrategy.STEP_UP_EMI_LOAN);
    }

    @Test
    @DisplayName("Should return null (auto-reject) when DTI exceeds maximum threshold")
    void suggestStrategy_Null_WhenDtiIsTooHigh() {
        // Arrange: 1% above the maximum allowed threshold
        BigDecimal highDti = LoanConstants.DTI_MID_THRESHOLD.add(BigDecimal.ONE);

        // Act
        LoanStrategy strategy = dtiService.suggestStrategy(highDti, 24);

        // Assert
        assertThat(strategy).isNull();
    }

    @Test
    @DisplayName("Should pass income validation for positive income")
    void validateIncome_Success() {
        // Act & Assert (Should not throw any exception)
        dtiService.validateIncome(new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("Should throw BusinessRuleException for zero income")
    void validateIncome_ThrowsException_ForZero() {
        // Act & Assert
        assertThatThrownBy(() -> dtiService.validateIncome(BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Monthly income must be positive for DTI calculation.");
    }

    @Test
    @DisplayName("Should throw BusinessRuleException for negative income")
    void validateIncome_ThrowsException_ForNegative() {
        // Act & Assert
        assertThatThrownBy(() -> dtiService.validateIncome(new BigDecimal("-1000.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Monthly income must be positive for DTI calculation.");
    }
}