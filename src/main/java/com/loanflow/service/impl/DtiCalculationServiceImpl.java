package com.loanflow.service.impl;

import com.loanflow.constants.LoanConstants;
import com.loanflow.enums.LoanStrategy;
import com.loanflow.exception.BusinessRuleException;
import com.loanflow.service.DtiCalculationService;
import com.loanflow.util.MoneyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class DtiCalculationServiceImpl implements DtiCalculationService {

    public BigDecimal calculateInitialDti(
            BigDecimal internalEmi,
            BigDecimal externalEmi,
            BigDecimal monthlyIncome ) {

        validateIncome(monthlyIncome);

        BigDecimal totalEmi = internalEmi.add(externalEmi);

        BigDecimal dti = totalEmi
                .divide(monthlyIncome, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Initial DTI calculated: {}%", dti);
        return dti;
    }

    public BigDecimal calculateFinalDti(
            BigDecimal internalEmi,
            BigDecimal externalEmi,
            BigDecimal newLoanEmi,
            BigDecimal monthlyIncome) {

        validateIncome(monthlyIncome);

        BigDecimal totalEmi = internalEmi
                .add(externalEmi)
                .add(newLoanEmi);

        BigDecimal dti = totalEmi
                .divide(monthlyIncome, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Final DTI calculated: {}%", dti);
        return dti;
    }

    public LoanStrategy suggestStrategy(BigDecimal dtiInitial, int tenureMonths) {
        if(dtiInitial.compareTo(LoanConstants.DTI_LOW_THRESHOLD) < 0) {
            return LoanStrategy.FLAT_RATE_LOAN;
        }
        if(dtiInitial.compareTo(LoanConstants.DTI_MID_THRESHOLD) <= 0) {
            return tenureMonths < LoanConstants.STEP_UP_TENURE_THRESHOLD
                    ? LoanStrategy.REDUCING_BALANCE_LOAN
                    : LoanStrategy.STEP_UP_EMI_LOAN;
        }
        return null;  // DTI > 40% → REJECT
    }

    private void validateIncome(BigDecimal income) {
        if (MoneyUtil.isZeroOrNegative(income)) {
            throw new BusinessRuleException("Monthly income must be positive for DTI calculation.");
        }
    }
}
