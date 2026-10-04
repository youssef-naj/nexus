package com.l2c.nexus;

import com.l2c.nexus.identity.application.RecordingAccountEmails;
import com.l2c.nexus.team.application.RecordingInvitationEmails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }

    @Bean
    RecordingAccountEmails recordingAccountEmails() {
        return new RecordingAccountEmails();
    }

    @Bean
    RecordingInvitationEmails recordingInvitationEmails() {
        return new RecordingInvitationEmails();
    }
}
