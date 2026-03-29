package com.loanflow.service.impl;

import com.loanflow.dto.response.EmiScheduleResponse;
import com.loanflow.service.EmiScheduleService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmiScheduleServiceImpl implements EmiScheduleService {

    @Override
    public List<EmiScheduleResponse> getScheduleByLoanNumber(String loanNumber) {
        return List.of();
    }
}
