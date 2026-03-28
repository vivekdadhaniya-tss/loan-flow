package com.loanflow.entity;

import com.loanflow.entity.base.BaseEntity;
import com.loanflow.entity.user.User;
import com.loanflow.enums.ApplicationStatus;
import com.loanflow.enums.LoanStrategy;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "loan_applications")
@Getter @Setter @NoArgsConstructor
public class LoanApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private User borrower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;               // null until officer takes action

    @NotNull
    @Positive
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @NotNull @Min(1) @Max(360)
    @Column(nullable = false)
    private Integer tenureMonths;

    @NotNull @Positive
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @NotNull @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal existingMonthlyEmi;

    @Column(precision = 5, scale = 2)
    private BigDecimal calculatedDti;

    @Enumerated(EnumType.STRING)
    private LoanStrategy suggestedStrategy;

    @Enumerated(EnumType.STRING)
    private LoanStrategy finalStrategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(length = 500)
    private String rejectionReason;

    private LocalDateTime reviewedAt;
}