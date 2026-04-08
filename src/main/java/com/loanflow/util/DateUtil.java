package com.loanflow.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DateUtil {

    private DateUtil() {}

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // EMI due date generation
    public static LocalDate emiDueDate(LocalDate disbursedOn, int installmentNum) {
        return disbursedOn.plusMonths(installmentNum);
    }

    // Scheduler helpers

    /**
     * Returns true if the given date is strictly before today.
     * Used by OverdueScheduler to find missed EMIs.
     *
     * @param date the EMI due date to check
     */
    public static boolean isBeforeToday(LocalDate date) {
        return date.isBefore(LocalDate.now());
    }

    /**
     * Returns a date N days from today.
     * Used by PaymentReminderScheduler:
     *   daysFromToday(LoanConstants.PAYMENT_REMINDER_DAYS_BEFORE)
     *   -> finds all EMIs due exactly 3 days from now.
     *
     * @param days number of days ahead
     */
    public static LocalDate daysFromToday(int days) {
        return LocalDate.now().plusDays(days);
    }

    /**
     * Number of days between two dates (always positive).
     * Used by OverdueMonitorService to set tracker.daysOverdue.
     *
     * @param from  earlier date (EMI due date)
     * @param to    later date (today)
     * @return days elapsed — 0 if same day
     */
    public static int daysBetween(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to);
    }

    // Formatting
    public static String format(LocalDate date) {
        if (date == null) return "";
        return date.format(DISPLAY_FORMAT);
    }
}

