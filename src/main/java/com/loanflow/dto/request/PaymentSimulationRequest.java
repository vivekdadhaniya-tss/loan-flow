package com.loanflow.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSimulationRequest{

    @NotBlank(message = "Loan number is required")
    private String loanNumber;

    @Min(value = 1, message = "Installment count must be at least 1")
    private Integer installmentCount = 1;

}