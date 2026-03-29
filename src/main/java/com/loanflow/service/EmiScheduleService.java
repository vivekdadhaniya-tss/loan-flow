package com.loanflow.service;

import com.loanflow.dto.response.EmiScheduleResponse;

import java.util.List;

public interface EmiScheduleService {
    List<EmiScheduleResponse> getScheduleByLoanNumber(String loanNumber);
}
