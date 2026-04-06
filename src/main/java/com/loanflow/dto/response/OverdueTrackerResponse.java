package com.loanflow.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OverdueTrackerResponse {

    private String loanNumber;

    private Integer installmentNumber;

    private String borrowerName;

    private String borrowerEmail;

    private LocalDate dueDate;

    private Integer daysOverdue;

    private BigDecimal fixedPenaltyAmount;

    private BigDecimal penaltyRate;

    private BigDecimal penaltyCharge;

    private Boolean penaltySettled;

    private Integer alertCount;

    private LocalDateTime detectedAt;

    private LocalDateTime resolvedAt;
}

