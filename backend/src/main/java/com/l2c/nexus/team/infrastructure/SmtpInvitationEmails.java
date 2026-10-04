package com.l2c.nexus.team.infrastructure;

import com.l2c.nexus.team.application.InvitationEmails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Plain-text invitation email. Only active when spring.mail.host is configured. */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
class SmtpInvitationEmails implements InvitationEmails {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpInvitationEmails(JavaMailSender mailSender, @Value("${nexus.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendInvitation(
            String toEmail,
            String organizationName,
            String inviterName,
            String role,
            String acceptLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        // The organization name cannot contain control characters (validated), so no header
        // injection
        message.setSubject("You've been invited to " + organizationName + " on Nexus");
        message.setText(
                inviterName
                        + " invited you to join "
                        + organizationName
                        + " on Nexus as "
                        + role.toLowerCase()
                        + ".\n\nOpen this link while signed in with this email address:\n\n"
                        + acceptLink
                        + "\n\nIf you don't have an account yet, create one with this email address"
                        + " first. The invitation expires in 7 days. If you weren't expecting it,"
                        + " you can ignore this message.\n");
        mailSender.send(message);
    }
}
