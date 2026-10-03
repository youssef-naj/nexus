package com.l2c.nexus.organization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class CurrentOrgArgumentResolverTest {

    private final CurrentOrgArgumentResolver resolver = new CurrentOrgArgumentResolver();

    @Test
    void failsLoudlyWhenARouteWasNotBehindTheGate() {
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not behind the organization gate");
    }

    @Test
    void returnsTheContextTheGateStored() {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        OrgContext context =
                new OrgContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        OrgRole.MANAGER,
                        OrganizationStatus.ACTIVE);
        raw.setAttribute(OrgScopeFilter.ATTRIBUTE, context);

        Object resolved = resolver.resolveArgument(null, null, new ServletWebRequest(raw), null);

        assertThat(resolved).isSameAs(context);
    }
}
