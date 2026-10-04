package com.l2c.nexus.team.application;

public class InvalidInvitationException extends RuntimeException {

    public InvalidInvitationException() {
        super("Invalid or expired invitation");
    }
}
