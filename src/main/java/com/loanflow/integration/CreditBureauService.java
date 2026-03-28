package com.loanflow.integration;

import com.loanflow.constants.LoanConstants;
import com.loanflow.exception.CreditBureauException;
import com.loanflow.integration.dto.CreditBureauResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditBureauService {

    private final CreditBureauClient client;

    public ExternalDebtResult fetchExternalEmi(String panNumber, BigDecimal selfDeclaredEmi) {
        try {
            CreditBureauResponse response = client.fetchReport(panNumber);

            BigDecimal emi = (response != null && response.getTotalMonthlyEmi() != null)
                    ? response.getTotalMonthlyEmi()
                    : BigDecimal.ZERO;

            return new ExternalDebtResult(emi, LoanConstants.BUREAU_STATUS_AVAILABLE);

        } catch (CreditBureauException e) {
            log.warn("Bureau unavailable — using self-declared EMI: {}", selfDeclaredEmi);
            return new ExternalDebtResult(BigDecimal.ZERO, LoanConstants.BUREAU_STATUS_UNAVAILABLE);
        }

    }

    public record ExternalDebtResult(BigDecimal externalMonthlyEmi, String bureauStatus) { }
}
