package com.loanflow.util;

import com.loanflow.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {

    private MoneyUtil() {}

    // Rounding
    public static BigDecimal roundHalfUp(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal roundPrecision(BigDecimal value) {
        return value.setScale(10, RoundingMode.HALF_UP);
    }

    // Final installment adjustment
    public static BigDecimal adjustFinalEmi(
            BigDecimal remainingBalance,
            BigDecimal lastMonthInterest) {
        return roundHalfUp(remainingBalance.add(lastMonthInterest));
    }

    // Guard validations
    public static void validatePositive(
            BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(
                    fieldName + " must be a positive amount.");
        }
    }

    public static boolean isZeroOrNegative(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0;
    }
}

