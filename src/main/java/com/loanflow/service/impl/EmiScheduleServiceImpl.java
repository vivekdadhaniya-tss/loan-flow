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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmiScheduleServiceImpl implements EmiScheduleService {

    private final EmiScheduleRepository emiScheduleRepository;
    private final LoanRepository loanRepository;
    private final EmiScheduleMapper emiScheduleMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<EmiScheduleResponse> getScheduleByLoanNumber(String loanNumber, int page, int size) {

        Loan loan = loanRepository.findByLoanNumber(loanNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanNumber));

        // Create the Pageable object
        Pageable pageable = PageRequest.of(page, size);

        // Fetch Paginated data from DB
        Page<EmiSchedule> schedulePage = emiScheduleRepository
                .findByLoanOrderByInstallmentNumberAsc(loan, pageable);

        // Map the Entity Page to Response DTO Page
        return schedulePage.map(emiScheduleMapper::toResponse);
    }


    @Override
    @Transactional
    public BigDecimal generateSchedule(Loan loan, EmiCalculationStrategy strategy) {

        List<EmiSchedule> schedule = strategy.generateEmiSchedule(loan);

        // Bulk insert — one SQL statement instead of N individual inserts
        emiScheduleRepository.saveAll(schedule);

        log.info("Generated {} EMI installments for loan {}", schedule.size(), loan.getId());
        return schedule.get(0).getTotalEmiAmount();
    }


    @Override
    @Transactional(readOnly = true)
    public List<EmiScheduleResponse> getScheduleByLoan(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));

        List<EmiSchedule> schedule = emiScheduleRepository.findByLoanOrderByInstallmentNumberAsc(loan);
        return emiScheduleMapper.toResponseList(schedule);
    }
}
