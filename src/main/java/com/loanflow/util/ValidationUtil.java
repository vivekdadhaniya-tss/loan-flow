package com.loanflow.util;

import com.loanflow.entity.Loan;
import com.loanflow.entity.LoanApplication;
import com.loanflow.enums.ApplicationStatus;
import com.loanflow.enums.LoanStatus;
import com.loanflow.exception.BusinessRuleException;
import com.loanflow.exception.InvalidStatusTransitionException;


public final class ValidationUtil {

    private ValidationUtil() {}

    // Payment guards
    public static void ensureLoanIsActive(Loan loan) {
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Payments can only be made on ACTIVE loans. " +
                            "Current status: " + loan.getStatus());
        }
    }

    // Application guards
    public static void ensureApplicationIsReviewable(
            LoanApplication application) {
        if (application.getStatus() != ApplicationStatus.PENDING
                && application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
            throw new InvalidStatusTransitionException(
                    "Application " + application.getId()
                            + " cannot be reviewed. Current status: "
                            + application.getStatus());
        }
    }

    public static void ensureApplicationIsCancellable(
            LoanApplication application) {
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING applications can be cancelled.");
        }
    }
}

