package com.l2c.nexus.identity.infrastructure;

import com.l2c.nexus.identity.application.AccountEmails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Plain-text emails over SMTP. Only active when spring.mail.host is configured. */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
class SmtpAccountEmails implements AccountEmails {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpAccountEmails(JavaMailSender mailSender, @Value("${nexus.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String displayName, String verificationLink) {
        send(
                toEmail,
                "Verify your email address for Nexus",
                "Hello "
                        + displayName
                        + ",\n\nPlease confirm your email address by opening this link:\n\n"
                        + verificationLink
                        + "\n\nThe link expires in 24 hours. If you did not create a Nexus"
                        + " account, you can ignore this message.\n");
    }

    @Override
    public void sendAlreadyRegisteredEmail(String toEmail) {
        send(
                toEmail,
                "Your Nexus account",
                "Someone, hopefully you, tried to create a Nexus account with this email"
                        + " address, but an account already exists.\n\nIf it was you, sign in"
                        + " instead. If not, you can ignore this message. Your account is safe"
                        + " and unchanged.\n");
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
