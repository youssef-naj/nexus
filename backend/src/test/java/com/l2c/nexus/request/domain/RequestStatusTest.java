package com.l2c.nexus.request.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestStatusTest {

    @Test
    void draftsAndSentBackRequestsAreEditable() {
        assertThat(RequestStatus.DRAFT.isEditable()).isTrue();
        assertThat(RequestStatus.CHANGES_REQUESTED.isEditable()).isTrue();
    }

    @Test
    void submittedAndDecidedRequestsAreNot() {
        assertThat(RequestStatus.SUBMITTED.isEditable()).isFalse();
        assertThat(RequestStatus.APPROVED.isEditable()).isFalse();
        assertThat(RequestStatus.REJECTED.isEditable()).isFalse();
    }
}
