package com.loanflow.listener;

import com.loanflow.entity.LoanApplication;
import com.loanflow.enums.ApplicationStatus;
import com.loanflow.enums.NotificationEventType;
import com.loanflow.event.*;
import com.loanflow.service.NotificationService;
import com.loanflow.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async @EventListener
    public void handleApplicationSubmitted(
            LoanApplicationSubmittedEvent event) {
        LoanApplication app = event.getApplication();
        notificationService.send(
                app.getBorrower(), null,
                NotificationEventType.APPLICATION_SUBMITTED,
                "Loan Application Received",
                "application-submitted",
                Map.of("borrowerName", app.getBorrower().getName(),
                        "amount", app.getRequestedAmount(),
                        "status", app.getStatus()));
    }

    @Async @EventListener
    public void handleLoanDecision(LoanDecisionEvent event) {
        boolean approved =
                event.getDecision() == ApplicationStatus.APPROVED;
        notificationService.send(
                event.getApplication().getBorrower(),
                event.getLoan(),
                approved ? NotificationEventType.LOAN_APPROVED
                        : NotificationEventType.LOAN_REJECTED,
                approved ? "Your Loan is Approved"
                        : "Loan Application Update",
                approved ? "loan-approved" : "loan-rejected",
                approved ?
                        Map.of(
                                "borrowerName", event.getApplication().getBorrower().getName(),
                                "loanId",       event.getLoan().getId(),
                                "amount",       event.getLoan().getApprovedAmount(),
                                "strategy",     event.getLoan().getStrategy().name(),
                                "tenure",       event.getLoan().getTenureMonths(),
                                "monthlyEmi",   event.getLoan().getMonthlyEmi()
                        ) :

                        Map.of("borrowerName",
                                event.getApplication().getBorrower().getName(),
                                "reason",
                                event.getRejectionReason() != null
                                        ? event.getRejectionReason() : "")
                );
    }

    @Async @EventListener
    public void handlePaymentReceived(PaymentReceivedEvent event) {
        notificationService.send(
                event.getPayment().getBorrower(),
                event.getPayment().getLoan(),
                NotificationEventType.EMI_PAID,
                "EMI Payment Confirmed",
                "emi-paid",
                Map.of(
                        "borrowerName",  event.getPayment().getBorrower().getName(),
                        "installmentNo", event.getEmiSchedule().getInstallmentNumber(),
                        "amount",        event.getPayment().getPaidAmount(),
                        "loanId",        event.getPayment().getLoan().getId()
                )
        );
    }

    @Async @EventListener
    public void handleOverdueAlert(OverdueAlertEvent event) {
        notificationService.send(
                event.getTracker().getBorrower(),
                event.getTracker().getLoan(),
                NotificationEventType.OVERDUE_ALERT,
                "Overdue EMI Alert",
                "overdue-alert",
                Map.of(
                        "borrowerName", event.getTracker().getBorrower().getName(),
                        "dueDate",      DateUtil.format(event.getTracker().getDueDate()),
                        "penalty",      event.getTracker().getPenaltyCharge(),
                        "daysOverdue",  event.getTracker().getDaysOverdue(),
                        "loanId",       event.getTracker().getLoan().getId()
                )
        );
    }

    @Async @EventListener
    public void handleLoanClosed(LoanClosedEvent event) {
        notificationService.send(
                event.getLoan().getBorrower(),
                event.getLoan(),
                NotificationEventType.LOAN_CLOSED,
                "Congratulations — Loan Fully Repaid",
                "loan-closed",
                Map.of("borrowerName",
                        event.getLoan().getBorrower().getName(),
                        "loanId",       event.getLoan().getId()
                ));
    }

    @Async @EventListener
    public void handlePaymentReminder(PaymentReminderEvent event) {
        notificationService.send(
                event.getEmiSchedule().getLoan().getBorrower(),
                event.getEmiSchedule().getLoan(),
                NotificationEventType.PAYMENT_REMINDER,
                "Payment Reminder — EMI Due Soon",
                "payment-reminder",
                Map.of(
                        "borrowerName", event.getEmiSchedule().getLoan().getBorrower().getName(),
                        "dueDate",      DateUtil.format(event.getEmiSchedule().getDueDate()),
                        "amount",       event.getEmiSchedule().getTotalEmiAmount(),
                        "loanId",       event.getEmiSchedule().getLoan().getId()
                )
        );
    }
}

