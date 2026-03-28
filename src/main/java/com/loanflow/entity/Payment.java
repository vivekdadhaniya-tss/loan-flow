package com.loanflow.entity;

import com.loanflow.entity.base.BaseEntity;
import com.loanflow.entity.user.User;
import com.loanflow.enums.EmiStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.*;


@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emi_schedule_id", nullable = false, unique = true)
    private EmiSchedule emiSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private User borrower;

    @NotNull @Positive
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount;

    @Column(nullable = false)
    private String paymentMode = "SIMULATION";

    @Column(nullable = false)
    private LocalDateTime paidAt;
}