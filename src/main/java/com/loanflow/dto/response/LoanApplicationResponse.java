package com.loanflow.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loanflow.enums.ApplicationStatus;
import com.loanflow.enums.BureauStatus;
import com.loanflow.enums.LoanStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanApplicationResponse {
    private Long id;
    private String applicationNumber;
    private BigDecimal requestedAmount;
    private Integer tenureMonths;
    private BigDecimal monthlyIncome;
    private BigDecimal existingMonthlyEmi;
    private BigDecimal calculatedDti;
    private LoanStrategy suggestedStrategy;
    private LoanStrategy finalStrategy;
    private ApplicationStatus status;
    private String rejectionReason;
    private BureauStatus bureauStatus;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewedByName;
}