package com.loanflow.service.impl;

import com.loanflow.dto.response.LoanResponse;
import com.loanflow.entity.user.User;
import com.loanflow.service.LoanService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LoanServiceImpl implements LoanService {
    @Override
    public List<LoanResponse> getMyLoans(User borrower) {
        return List.of();
    }
}
