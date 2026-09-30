package com.l2c.nexus.identity.application;

public interface AccountEmails {

    void sendVerificationEmail(String toEmail, String displayName, String verificationLink);

    void sendAlreadyRegisteredEmail(String toEmail);
}
