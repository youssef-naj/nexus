package com.l2c.nexus.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.PlatformFixtures;
import com.l2c.nexus.support.PlatformFixtures.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CurrentUserPlatformFlagIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private PlatformFixtures fixtures;

    @Test
    void ordinaryUsersAreNotPlatformAdministrators() {
        Account user = fixtures.signedIn(port, false);

        assertThat(user.http().get("/api/auth/me").body()).contains("\"platformAdmin\":false");
    }

    @Test
    void administratorsAreToldSoAndLoseTheFlagWhenDemoted() {
        Account admin = fixtures.signedIn(port, true);
        assertThat(admin.http().get("/api/auth/me").body()).contains("\"platformAdmin\":true");

        fixtures.setPlatformAdmin(admin.userId(), false);

        assertThat(admin.http().get("/api/auth/me").body()).contains("\"platformAdmin\":false");
    }
}
