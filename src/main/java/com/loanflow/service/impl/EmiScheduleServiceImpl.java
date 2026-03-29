package com.loanflow.service.impl;

import com.loanflow.dto.response.EmiScheduleResponse;
import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.mapper.EmiScheduleMapper;
import com.loanflow.repository.EmiScheduleRepository;
import com.loanflow.repository.LoanRepository;
import com.loanflow.service.EmiScheduleService;
import com.loanflow.strategy.EmiCalculationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmiScheduleServiceImpl implements EmiScheduleService {

    private final EmiScheduleRepository emiScheduleRepository;
    private final LoanRepository loanRepository;
    private final EmiScheduleMapper emiScheduleMapper;

    @Override
    @Transactional
    public BigDecimal generateSchedule(Loan loan, EmiCalculationStrategy strategy) {
        List<EmiSchedule> schedule = strategy.generateEmiSchedule(loan);
        emiScheduleRepository.saveAll(schedule);    // bulk insertion
        log.info("Generated {} installments for loan {}" + schedule.size(), loan.getId());
        return schedule.get(0).getTotalEmiAmount();   // return first EMI amount as reference
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmiScheduleResponse> getScheduleByLoan(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));

        List<EmiSchedule> schedules = emiScheduleRepository.findByLoanOrderByInstallmentNumberAsc(loan);
        return emiScheduleMapper.toResponseList(schedules);
    }
}
