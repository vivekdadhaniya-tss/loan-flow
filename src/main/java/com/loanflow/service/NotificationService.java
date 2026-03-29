package com.loanflow.service;

import com.loanflow.entity.Loan;
import com.loanflow.entity.Notification;
import com.loanflow.entity.user.User;
import com.loanflow.enums.NotificationEventType;

import java.util.Map;

public interface NotificationService {
    void send(
            User recipient,
            Loan loan,
            NotificationEventType eventType,
            String subject,
            String templateName,
            Map<String, Object> model);

    void resend(Notification notification);
}
