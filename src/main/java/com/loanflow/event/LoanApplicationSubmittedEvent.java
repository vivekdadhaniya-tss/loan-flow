package com.loanflow.event;

import com.loanflow.entity.LoanApplication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
public class LoanApplicationSubmittedEvent {

    private final LoanApplication application;
}
