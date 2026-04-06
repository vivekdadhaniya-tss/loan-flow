package com.loanflow.dto.response;

import com.loanflow.enums.PenaltyStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowerOverdueResponse {

    private String loanNumber;
    private Integer installmentNumber;

    private LocalDate dueDate;
    private Integer daysOverdue;

    private BigDecimal fixedPenaltyAmount;
    private BigDecimal penaltyCharge;
    private BigDecimal totalPenaltyAmount; //  calculate this in the mapper

    private String penaltyStatus;
}