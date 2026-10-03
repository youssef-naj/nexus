package com.l2c.nexus.organization.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import com.l2c.nexus.identity.persistence.UserRepository;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.membership.application.MembershipView;
import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrganizationServiceIntegrationTest {

    private static final String STRONG = "correct horse battery staple";

    @Autowired private OrganizationService organizations;
    @Autowired private MembershipService memberships;
    @Autowired private RegistrationService registration;
    @Autowired private UserRepository users;
    @Autowired private JdbcClient jdbc;

    private UUID newUser() {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        registration.register(new RegisterUserCommand(email, STRONG, "Owner"));
        return users.findByEmailCaseInsensitive(email).orElseThrow().getId();
    }

    private static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void createsTheOrganizationTheOwnerMembershipAndTheAuditEventTogether() {
        UUID creator = newUser();
        String name = uniqueName("Acme");

        MyOrganization created =
                organizations.create(creator, "  " + name.replace(" ", "   ") + " ");

        assertThat(created.name()).isEqualTo(name);
        assertThat(created.role()).isEqualTo(OrgRole.OWNER);
        assertThat(created.status()).isEqualTo(OrganizationStatus.ACTIVE);

        List<MembershipView> mine = memberships.activeMembershipsOf(creator);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).organizationId()).isEqualTo(created.id());
        assertThat(mine.get(0).role()).isEqualTo(OrgRole.OWNER);

        String audit =
                jdbc.sql(
                                "SELECT event_type || '|' || (metadata ->> 'name') || '|' ||"
                                        + " (metadata ->> 'slug') FROM audit_logs"
                                        + " WHERE actor_user_id = ? AND organization_id = ?")
                        .params(creator, created.id())
                        .query(String.class)
                        .single();
        assertThat(audit).isEqualTo("ORGANIZATION_CREATED|" + name + "|" + created.slug());
    }

    @Test
    void organizationsWithTheSameNameGetDistinctSlugs() {
        UUID creator = newUser();
        String name = uniqueName("Twin");

        MyOrganization first = organizations.create(creator, name);
        MyOrganization second = organizations.create(creator, name);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.slug()).isNotEqualTo(first.slug());
        assertThat(second.slug()).matches(first.slug() + "-[0-9a-f]{8}");
    }

    @Test
    void eachUserSeesOnlyTheirOwnOrganizationsWithTheirOwnRole() {
        UUID ada = newUser();
        UUID eve = newUser();
        MyOrganization adaFirst = organizations.create(ada, uniqueName("Ada"));
        MyOrganization adaSecond = organizations.create(ada, uniqueName("Ada"));
        MyOrganization eveOnly = organizations.create(eve, uniqueName("Eve"));

        assertThat(organizations.listFor(ada))
                .extracting(MyOrganization::id)
                .containsExactlyInAnyOrder(adaFirst.id(), adaSecond.id())
                .doesNotContain(eveOnly.id());
        assertThat(organizations.listFor(eve))
                .extracting(MyOrganization::id)
                .containsExactly(eveOnly.id());
        assertThat(organizations.listFor(UUID.randomUUID())).isEmpty();
    }
}
