package com.loanflow.strategy;

import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;

import java.math.BigDecimal;
import java.util.List;

public interface EmiCalculationStrategy {

    List<EmiSchedule> generateEmiSchedule(Loan loan);

    BigDecimal calculateBaseEmi(Loan loan);
}
