package com.loanflow.constants;

import java.math.BigDecimal;


public final class LoanConstants {

    private LoanConstants() {}

    // DTI thresholds
    // Used by: DtiCalculationService.suggestStrategy()
    public static final BigDecimal DTI_LOW_THRESHOLD = new BigDecimal("20.00");
    public static final BigDecimal DTI_MID_THRESHOLD = new BigDecimal("40.00");
    public static final int STEP_UP_TENURE_THRESHOLD = 24;

    // Loan limits
    // Used by: LoanApplicationService.apply()
    public static final int MAX_ACTIVE_LOANS = 3;
    public static final long MIN_REQUESTED_AMOUNT = 10000L;
    public static final String MAX_LOAN_AMOUNT = "50000000.00";
    public static final int MIN_TENURE_MONTHS = 6;
    public static final int MAX_TENURE_MONTHS = 85;

    // Interest rate limits
    // Used by: LoanDecisionRequest validation, LoanService
    public static final BigDecimal MAX_INTEREST_RATE_PA = new BigDecimal("36.00");

    // Step-Up EMI
    // Each new calendar year, EMI = previous-year EMI × STEP_UP_GROWTH_MULTIPLIER.
    // STEP_UP_GROWTH_MULTIPLIER = 1 + (rate / 100)
    public static final BigDecimal STEP_UP_GROWTH_MULTIPLIER = new BigDecimal("1.05");

    // Overdue & penalty
    // Used by: OverdueMonitorService.scanAndMarkOverdue()
    public static final BigDecimal LATE_FEE_FLAT_AMOUNT = new BigDecimal("500.00");

    // Daily penalty rate (0.05% per day)
    public static final BigDecimal OVERDUE_DAILY_PENALTY_RATE = new BigDecimal("0.0005");

    // Grace period before daily penalty applies
    public static final int PENALTY_GRACE_DAYS = 30;

    // Days after which loan becomes DEFAULTED (NPA standard)
    public static final int DEFAULT_THRESHOLD_DAYS = 90;

    /**
     * Number of days a loan stays DEFAULTED before transitioning
     * to WRITTEN_OFF status.
     */
    public static final int WRITTEN_OFF_DAYS = 180;

    // Notification
    // Used by: NotificationRetryScheduler, PaymentReminderScheduler
    public static final int MAX_NOTIFICATION_RETRIES = 3;
    // PaymentReminderScheduler queries: dueDate = today + PAYMENT_REMINDER_DAYS_BEFORE
    public static final int PAYMENT_REMINDER_DAYS_BEFORE = 3;

    // Credit Bureau
    // Used by: LoanApplicationService, LoanApplication entity
    public static final String BUREAU_STATUS_AVAILABLE   = "AVAILABLE";
    public static final String BUREAU_STATUS_UNAVAILABLE = "UNAVAILABLE";
}

