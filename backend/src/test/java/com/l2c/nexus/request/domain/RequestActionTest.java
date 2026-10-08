package com.l2c.nexus.request.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RequestActionTest {

    @Test
    void submitIsAllowedFromDraftAndChangesRequestedOnly() {
        for (RequestStatus status : RequestStatus.values()) {
            boolean expected =
                    status == RequestStatus.DRAFT || status == RequestStatus.CHANGES_REQUESTED;
            assertThat(RequestAction.SUBMIT.allowedFrom(status))
                    .as("SUBMIT from " + status)
                    .isEqualTo(expected);
        }
    }

    @Test
    void reviewActionsAreAllowedFromSubmittedOnly() {
        for (RequestAction action :
                Arrays.stream(RequestAction.values()).filter(RequestAction::isReview).toList()) {
            for (RequestStatus status : RequestStatus.values()) {
                assertThat(action.allowedFrom(status))
                        .as(action + " from " + status)
                        .isEqualTo(status == RequestStatus.SUBMITTED);
            }
        }
    }

    @Test
    void targetsFollowTheWorkflow() {
        assertThat(RequestAction.SUBMIT.target()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(RequestAction.APPROVE.target()).isEqualTo(RequestStatus.APPROVED);
        assertThat(RequestAction.REJECT.target()).isEqualTo(RequestStatus.REJECTED);
        assertThat(RequestAction.REQUEST_CHANGES.target())
                .isEqualTo(RequestStatus.CHANGES_REQUESTED);
    }

    @Test
    void rejectAndRequestChangesRequireAComment() {
        assertThat(RequestAction.REJECT.commentRequired()).isTrue();
        assertThat(RequestAction.REQUEST_CHANGES.commentRequired()).isTrue();
        assertThat(RequestAction.APPROVE.commentRequired()).isFalse();
        assertThat(RequestAction.SUBMIT.commentRequired()).isFalse();
    }

    @Test
    void decidedRequestsAcceptNoAction() {
        for (RequestAction action : RequestAction.values()) {
            assertThat(action.allowedFrom(RequestStatus.APPROVED)).isFalse();
            assertThat(action.allowedFrom(RequestStatus.REJECTED)).isFalse();
        }
    }
}
