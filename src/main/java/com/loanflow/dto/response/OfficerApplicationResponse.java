package com.loanflow.dto.response;

import com.loanflow.enums.ApplicationStatus;
import com.loanflow.enums.BureauStatus;
import com.loanflow.enums.LoanStrategy;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OfficerApplicationResponse {
    private String applicationNumber;
    private BigDecimal requestedAmount;
    private Integer tenureMonths;
    private BigDecimal monthlyIncome;
    private BigDecimal existingMonthlyEmi;
    private BigDecimal calculatedDti;
    private LoanStrategy suggestedStrategy;
    private LoanStrategy finalStrategy;
    private ApplicationStatus status;
    private BureauStatus bureauStatus;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewedByName;
}
