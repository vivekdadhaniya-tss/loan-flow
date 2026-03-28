package com.loanflow.strategy;

import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlatRateStrategy implements EmiCalculationStrategy {
    @Override
    public List<EmiSchedule> generateEmiSchedule(Loan loan) {
        return List.of();
    }
}
