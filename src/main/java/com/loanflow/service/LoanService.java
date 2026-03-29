package com.loanflow.service;

import com.loanflow.dto.response.LoanResponse;
import com.loanflow.entity.user.User;

import java.util.List;

public interface LoanService {
    List<LoanResponse> getMyLoans(User borrower);
}
