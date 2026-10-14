package com.l2c.nexus.organization.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StatusChangeReasonTest {

    private static Set<StatusChangeReason> allowedFor(OrganizationStatus target) {
        return Arrays.stream(StatusChangeReason.values())
                .filter(reason -> reason.allowedFor(target))
                .collect(Collectors.toSet());
    }

    @Test
    void suspensionHasItsOwnList() {
        assertThat(allowedFor(OrganizationStatus.SUSPENDED))
                .containsExactlyInAnyOrder(
                        StatusChangeReason.POLICY_VIOLATION,
                        StatusChangeReason.SECURITY_CONCERN,
                        StatusChangeReason.NON_PAYMENT,
                        StatusChangeReason.CUSTOMER_REQUEST,
                        StatusChangeReason.OTHER);
    }

    @Test
    void reactivationHasItsOwnList() {
        assertThat(allowedFor(OrganizationStatus.ACTIVE))
                .containsExactlyInAnyOrder(
                        StatusChangeReason.ISSUE_RESOLVED,
                        StatusChangeReason.SUSPENDED_IN_ERROR,
                        StatusChangeReason.CUSTOMER_REQUEST,
                        StatusChangeReason.OTHER);
    }

    @Test
    void everyReasonSuitsAtLeastOneAction() {
        for (StatusChangeReason reason : StatusChangeReason.values()) {
            assertThat(
                            reason.allowedFor(OrganizationStatus.SUSPENDED)
                                    || reason.allowedFor(OrganizationStatus.ACTIVE))
                    .as(reason.name())
                    .isTrue();
        }
    }
}
