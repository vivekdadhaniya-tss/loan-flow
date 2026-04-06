package com.loanflow.service;

import com.loanflow.dto.response.EmiScheduleResponse;
import com.loanflow.entity.Loan;
import com.loanflow.strategy.EmiCalculationStrategy;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

public interface EmiScheduleService {

//    BigDecimal generateSchedule(Loan loan, EmiCalculationStrategy strategy);

//    @Transactional(readOnly = true)
    Page<EmiScheduleResponse> getScheduleByLoanNumber(String loanNumber, int page, int size);

    @Transactional
    BigDecimal generateSchedule(Loan loan, EmiCalculationStrategy strategy);

    List<EmiScheduleResponse> getScheduleByLoan(Long loanId);
//    List<EmiScheduleResponse> getScheduleByLoanNumber(String loanNumber);
}
