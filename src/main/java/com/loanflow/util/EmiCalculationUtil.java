package com.loanflow.util;

import java.math.BigDecimal;
import java.math.RoundingMode;


public final class EmiCalculationUtil {

    private EmiCalculationUtil() {}

    // 1. Rate conversion
    public static BigDecimal calculateMonthlyRate(BigDecimal annualRatePercent) {
        return annualRatePercent
                .divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP);
    }

    // 2. Reducing balance EMI formula
    // EMI = P × r × (1+r)^n / ((1+r)^n - 1)
    // ReducingBalanceStrategy - as the fixed EMI for all installments.
    public static BigDecimal calculateReducingBalanceEmi(
            BigDecimal principal,
            BigDecimal monthlyRate,
            int tenureMonths) {

        // (1 + r)^n
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal power    = onePlusR.pow(tenureMonths);

        // P × r × (1+r)^n
        BigDecimal numerator = principal
                .multiply(monthlyRate)
                .multiply(power);

        // (1+r)^n - 1
        BigDecimal denominator = power.subtract(BigDecimal.ONE);

        return MoneyUtil.roundHalfUp(
                numerator.divide(denominator,
                10,
                RoundingMode.HALF_UP));
    }

    /**
     * P = Sum [ (E * growthMultiplier^yearIndex) / (1 + r)^month ]
     *
     * E = P / Sum [ growthMultiplier^yearIndex / (1 + r)^month ]
     *
     * where,
     * yearIndex = (month - 1) / 12
     * r = monthly rate
     * E = base emi (year-1 emi)
     * P = Principal
     */
    // 3. Step-Up Base EMI formula
    public static BigDecimal calculateStepUpBaseEmi(
            BigDecimal principal,
            BigDecimal monthlyRate,
            int tenureMonths,
            BigDecimal growthMultiplier) {

        BigDecimal denominator = BigDecimal.ZERO;
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);

        for (int i = 1; i <= tenureMonths; i++) {
            int yearIndex = (i - 1) / 12;

            // (1 + growthMultiplier)^yearIndex
            BigDecimal growthFactor = growthMultiplier.pow(yearIndex);

            // (1 + r)^i
            BigDecimal discountFactor = onePlusR.pow(i);

            // denominator += growthFactor / discountFactor
            BigDecimal term = growthFactor.divide(discountFactor, 10, RoundingMode.HALF_UP);
            denominator = denominator.add(term);
        }

        return MoneyUtil.roundHalfUp(principal.divide(denominator, 10, RoundingMode.HALF_UP));
    }

    // 4. Flat rate helpers
    public static BigDecimal calculateFlatMonthlyPrincipal(BigDecimal principal, int tenureMonths) {
        return MoneyUtil.roundHalfUp(
                principal.divide(
                        new BigDecimal(tenureMonths),
                        10,
                        RoundingMode.HALF_UP)
        );
    }

    public static BigDecimal calculateFlatMonthlyInterest(BigDecimal principal, BigDecimal monthlyRate) {
        return MoneyUtil.roundHalfUp(principal.multiply(monthlyRate));
    }
}

