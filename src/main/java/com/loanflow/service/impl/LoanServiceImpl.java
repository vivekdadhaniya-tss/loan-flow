package com.loanflow.service.impl;

import com.loanflow.mapper.LoanMapper;
import com.loanflow.repository.LoanApplicationRepository;
import com.loanflow.repository.LoanRepository;
import com.loanflow.service.*;
import com.loanflow.strategy.LoanStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class LoanServiceImpl implements LoanService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepository loanRepository;
    private final EmiScheduleService emiScheduleService;
    private final DtiCalculationService dtiCalculationService;
    private final LoanStatusTransitionService loanStatusTransitionService;
    private final LoanStrategyFactory loanStrategyFactory;
    private final AuditService auditService;
    private final LoanMapper loanMapper;
    private final ApplicationEventPublisher eventPublisher;

}
