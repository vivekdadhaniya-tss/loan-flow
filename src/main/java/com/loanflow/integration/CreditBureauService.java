package com.loanflow.integration;

public interface CreditBureauService {
    CreditBureauServiceImpl.ExternalDebtResult fetchExternalEmi(String panNumber);
}
