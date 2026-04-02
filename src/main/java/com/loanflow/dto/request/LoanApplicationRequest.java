package com.loanflow.dto.request;


import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LoanApplicationRequest {

    @NotNull(message = "Requested amount is required")
    @Positive(message = "Amount must be positive")
    @Min(value = 10000,   message = "Minimum requested amount is 10000")
    @DecimalMax(value = "50000000", message = "Amount exceeds maximum limit")
    private BigDecimal requestedAmount;

    @NotNull(message = "Tenure is required")
    @Min(value = 6,   message = "Minimum tenure is 6 month")
    @Max(value = 85, message = "Maximum tenure is 85 months")
    private Integer tenureMonths;

    @NotNull(message = "Monthly income is required")
    @Positive(message = "Monthly income must be positive")
    private BigDecimal monthlyIncome;

}