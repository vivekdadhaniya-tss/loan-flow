package com.loanflow.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loanflow.enums.ApplicationStatus;
import com.loanflow.enums.LoanStrategy;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BorrowerApplicationResponse {
    private String applicationNumber;
    private BigDecimal requestedAmount;
    private Integer tenureMonths;
    private BigDecimal monthlyIncome;
    private BigDecimal existingMonthlyEmi;
    private ApplicationStatus status;
    private LoanStrategy finalStrategy;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewedByName;
}