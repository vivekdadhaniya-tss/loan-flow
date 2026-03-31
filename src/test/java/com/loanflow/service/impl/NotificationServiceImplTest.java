package com.loanflow.service.impl;

import com.loanflow.entity.Loan;
import com.loanflow.entity.Notification;
import com.loanflow.entity.user.User;
import com.loanflow.enums.NotificationEventType;
import com.loanflow.enums.NotificationStatus;
import com.loanflow.repository.NotificationRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private User recipient;
    private Loan loan;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        recipient = new User();
        recipient.setId(UUID.randomUUID());
        recipient.setEmail("borrower@example.com");

        loan = new Loan();
        loan.setId(UUID.randomUUID());

        // The Magic Trick: Create a real MimeMessage with an empty session to prevent NPEs in MimeMessageHelper
        Session session = Session.getInstance(new Properties());
        mimeMessage = new MimeMessage(session);
    }

    @Test
    @DisplayName("Should successfully process template, save notification, send email, and update status to SENT")
    void send_Success() {
        // Arrange
        String templateName = "payment-reminder";
        String subject = "Your EMI is due";
        Map<String, Object> model = Map.of("borrowerName", "John Doe");
        String htmlOutput = "<html><body>Hello John Doe</body></html>";

        // Mock TemplateEngine to return our fake HTML
        when(templateEngine.process(eq("email/" + templateName), any(Context.class))).thenReturn(htmlOutput);

        // Mock MailSender to return our safe MimeMessage
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Mock Repository to just return whatever is passed into it
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.send(recipient, loan, NotificationEventType.PAYMENT_REMINDER, subject, templateName, model);

        // Assert
        verify(mailSender, times(1)).send(mimeMessage);

        // Repository save is called twice: once to queue, once to mark as sent. We capture all.
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());

        // Inspect the final state of the notification (the 2nd time it was saved)
        Notification finalNotification = notificationCaptor.getAllValues().get(1);

        assertThat(finalNotification.getRecipient()).isEqualTo(recipient);
        assertThat(finalNotification.getLoan()).isEqualTo(loan);
        assertThat(finalNotification.getDestination()).isEqualTo("borrower@example.com");
        assertThat(finalNotification.getEventType()).isEqualTo(NotificationEventType.PAYMENT_REMINDER);
        assertThat(finalNotification.getContent()).isEqualTo(htmlOutput);
        assertThat(finalNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(finalNotification.getSentAt()).isNotNull();
        assertThat(finalNotification.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("Should handle email failures gracefully, update status to FAILED, and save reason")
    void send_Failure() {
        // Arrange
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>HTML</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        // Simulate an SMTP server failure!
        doThrow(new MailSendException("SMTP server timeout")).when(mailSender).send(any(MimeMessage.class));

        // Act
        notificationService.send(recipient, loan, NotificationEventType.LOAN_APPROVED, "Subject", "template", Map.of());

        // Assert
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        Notification finalNotification = notificationCaptor.getAllValues().get(1);

        assertThat(finalNotification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(finalNotification.getFailureReason()).contains("SMTP server timeout");
        assertThat(finalNotification.getSentAt()).isNull();
    }

    @Test
    @DisplayName("Should successfully resend a previously failed notification")
    void resend_Success() {
        // Arrange
        Notification failedNotification = new Notification();
        failedNotification.setDestination("borrower@example.com");
        failedNotification.setSubject("Retry Subject");
        failedNotification.setContent("<html>Retry Content</html>");
        failedNotification.setStatus(NotificationStatus.FAILED);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        notificationService.resend(failedNotification);

        // Assert
        verify(mailSender, times(1)).send(mimeMessage);
        verify(notificationRepository, times(1)).save(failedNotification);

        assertThat(failedNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(failedNotification.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("Should increment retry count and capture new reason when resend fails")
    void resend_Failure() {
        // Arrange
        Notification failedNotification = new Notification();
        failedNotification.setDestination("borrower@example.com");
        failedNotification.setSubject("Retry Subject");
        failedNotification.setContent("<html>Retry Content</html>");
        failedNotification.setStatus(NotificationStatus.FAILED);
        failedNotification.setRetryCount(2); // Has already failed twice before

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessage.class));

        // Act
        notificationService.resend(failedNotification);

        // Assert
        verify(notificationRepository, times(1)).save(failedNotification);

        assertThat(failedNotification.getStatus()).isEqualTo(NotificationStatus.FAILED); // Status doesn't change
        assertThat(failedNotification.getRetryCount()).isEqualTo(3); // Incremented!
        assertThat(failedNotification.getFailureReason()).contains("Connection refused");
        assertThat(failedNotification.getSentAt()).isNull();
    }
}