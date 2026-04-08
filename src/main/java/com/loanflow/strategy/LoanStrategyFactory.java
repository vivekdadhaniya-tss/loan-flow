package com.loanflow.strategy;

import com.loanflow.enums.LoanStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LoanStrategyFactory {

    private final Map<String, EmiCalculationStrategy> strategyMap;

    public EmiCalculationStrategy resolve(LoanStrategy strategy) {
        EmiCalculationStrategy resolved = strategyMap.get(strategy.name());
        if (resolved == null) {
            throw new IllegalArgumentException("No strategy found for: " + strategy);
        }
        return resolved;
    }
}