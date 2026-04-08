package com.loanflow.util;

import java.math.BigDecimal;
import java.math.RoundingMode;


public final class EmiCalculationUtil {

    private EmiCalculationUtil() {}

    /**
     * Converts annual interest rate (%) to monthly decimal rate
     * Formula: annualRate / 100 / 12  = annualRate / 1200
     *
     *   12% p.a. -> 0.01 per month
     */
    // 1. Rate conversion
    public static BigDecimal calculateMonthlyRate(BigDecimal annualRatePercent) {
        return annualRatePercent
                .divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP);
    }

    /**
     * Standard reducing balance (PMT) formula:
     *   EMI = P × r × (1+r)^n / ((1+r)^n - 1)
     *
     * ReducingBalanceStrategy - as the fixed EMI for all installments.
     */
    // 2. Reducing balance EMI formula
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
     * Calculates the Year-1 (Base) EMI for a Step-Up loan.
     *
     * Since the EMI increases every 12 months, we find the starting EMI (E) such that:
     * Principal = Sum [ (E * growthRate^yearIndex) / (1 + r)^month ]
     * where yearIndex = (month - 1) / 12
     */
    // 3. Step-Up Base EMI formula
    public static BigDecimal calculateStepUpBaseEmi(
            BigDecimal principal,
            BigDecimal monthlyRate,
            int tenureMonths,
            BigDecimal growthRate) {

        BigDecimal denominator = BigDecimal.ZERO;
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);

        for (int i = 1; i <= tenureMonths; i++) {
            int yearIndex = (i - 1) / 12;

            // (1 + growthRate)^yearIndex
            BigDecimal growthFactor = growthRate.pow(yearIndex);

            // (1 + r)^i
            BigDecimal discountFactor = onePlusR.pow(i);

            // denominator += growthFactor / discountFactor
            BigDecimal term = growthFactor.divide(discountFactor, 10, RoundingMode.HALF_UP);
            denominator = denominator.add(term);
        }

        return MoneyUtil.roundHalfUp(principal.divide(denominator, 10, RoundingMode.HALF_UP));
    }

    // 4. Flat rate helpers
    public static BigDecimal calculateFlatMonthlyPrincipal(
            BigDecimal principal,
            int tenureMonths) {
        return MoneyUtil.roundHalfUp(
                principal.divide(
                        new BigDecimal(tenureMonths),
                        10,
                        RoundingMode.HALF_UP)
        );
    }

    /**
     * Formula: principal × monthlyRate
     *
     * In flat rate, interest is always on the ORIGINAL principal,
     * not the outstanding balance — this is what makes it "flat".
     *
     */
    public static BigDecimal calculateFlatMonthlyInterest(
            BigDecimal principal,
            BigDecimal monthlyRate) {
        return MoneyUtil.roundHalfUp(principal.multiply(monthlyRate));
    }
}

