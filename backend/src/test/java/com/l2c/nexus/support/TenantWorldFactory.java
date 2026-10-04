package com.l2c.nexus.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.identity.application.EmailVerificationService;
import com.l2c.nexus.identity.application.RecordingAccountEmails;
import com.l2c.nexus.identity.application.RegisterUserCommand;
import com.l2c.nexus.identity.application.RegistrationService;
import com.l2c.nexus.identity.persistence.UserRepository;
import com.l2c.nexus.membership.application.MembershipService;
import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.application.MyOrganization;
import com.l2c.nexus.organization.application.OrganizationService;
import com.l2c.nexus.support.TenantWorld.Member;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds a fresh TenantWorld through the real services and real logins. */
@Component
public class TenantWorldFactory {

    private static final String PASSWORD = "correct horse battery staple";

    private final RegistrationService registration;
    private final EmailVerificationService verification;
    private final RecordingAccountEmails emails;
    private final UserRepository users;
    private final OrganizationService organizations;
    private final MembershipService memberships;

    public TenantWorldFactory(
            RegistrationService registration,
            EmailVerificationService verification,
            RecordingAccountEmails emails,
            UserRepository users,
            OrganizationService organizations,
            MembershipService memberships) {
        this.registration = registration;
        this.verification = verification;
        this.emails = emails;
        this.users = users;
        this.organizations = organizations;
        this.memberships = memberships;
    }

    public TenantWorld create(int port) {
        Member alice = signUp("alice", port);
        Member ann = signUp("ann", port);
        Member dan = signUp("dan", port);
        Member carol = signUp("carol", port);
        Member frank = signUp("frank", port);
        Member bob = signUp("bob", port);
        Member erin = signUp("erin", port);

        MyOrganization orgA = organizations.create(alice.userId(), "Org A " + suffix());
        memberships.addMember(orgA.id(), ann.userId(), OrgRole.ADMIN);
        memberships.addMember(orgA.id(), dan.userId(), OrgRole.MANAGER);
        memberships.addMember(orgA.id(), carol.userId(), OrgRole.EMPLOYEE);
        memberships.addMember(orgA.id(), frank.userId(), OrgRole.EMPLOYEE);
        MyOrganization orgB = organizations.create(bob.userId(), "Org B " + suffix());

        return new TenantWorld(
                port, orgA.id(), orgB.id(), alice, ann, dan, carol, frank, bob, erin);
    }

    private Member signUp(String label, int port) {
        String email = label + "-" + UUID.randomUUID() + "@example.com";
        registration.register(new RegisterUserCommand(email, PASSWORD, label));
        verification.verify(emails.awaitNext(email).token());
        HttpTestClient http = new HttpTestClient(port);
        HttpResponse<String> login =
                http.postForm(
                        "/api/auth/login",
                        Map.of("email", email, "password", PASSWORD),
                        http.csrfToken());
        assertThat(login.statusCode()).isEqualTo(204);
        UUID userId = users.findByEmailCaseInsensitive(email).orElseThrow().getId();
        return new Member(userId, email, http);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
