package com.loanflow.dto.request;


import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

import static com.loanflow.constants.LoanConstants.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LoanApplicationRequest {

    @NotNull(message = "Requested amount is required")
    @Positive(message = "Amount must be positive")
    @Min(value = MIN_REQUESTED_AMOUNT,   message = "Minimum requested amount is 10000")
    @DecimalMax(value = MAX_LOAN_AMOUNT, message = "Amount exceeds maximum limit")
    private BigDecimal requestedAmount;

    @NotNull(message = "Tenure is required")
    @Min(value = MIN_TENURE_MONTHS,   message = "Minimum tenure is 6 month")
    @Max(value = MAX_TENURE_MONTHS, message = "Maximum tenure is 85 months")
    private Integer tenureMonths;

    @NotNull(message = "Monthly income is required")
    @Positive(message = "Monthly income must be positive")
    private BigDecimal monthlyIncome;

}