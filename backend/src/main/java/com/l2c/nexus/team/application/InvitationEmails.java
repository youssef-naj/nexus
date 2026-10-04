package com.l2c.nexus.team.application;

public interface InvitationEmails {

    void sendInvitation(
            String toEmail,
            String organizationName,
            String inviterName,
            String role,
            String acceptLink);
}
