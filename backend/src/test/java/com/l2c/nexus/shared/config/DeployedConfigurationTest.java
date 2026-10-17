package com.l2c.nexus.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Guards the deployment-relevant settings against accidental edits. */
class DeployedConfigurationTest {

    private static Properties load(String file) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(file));
        return Objects.requireNonNull(yaml.getObject(), file);
    }

    @Test
    void theProdProfileDefaultsToSecureCookiesFromOneSwitch() {
        Properties prod = load("application-prod.yaml");

        // Both flags read the same variable (default true), so they can never disagree
        assertThat(prod.getProperty("server.servlet.session.cookie.secure"))
                .isEqualTo("${NEXUS_SECURE_COOKIES:true}");
        assertThat(prod.getProperty("nexus.security.secure-cookies"))
                .isEqualTo("${NEXUS_SECURE_COOKIES:true}");
    }

    @Test
    void apiDocumentationIsOffByDefaultAndSessionsNeverTravelInUrls() {
        Properties base = load("application.yaml");

        assertThat(base.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(base.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(base.getProperty("server.servlet.session.tracking-modes")).isEqualTo("cookie");
        assertThat(base.getProperty("nexus.security.secure-cookies")).isEqualTo("false");
    }

    @Test
    void shutdownIsGracefulAndBounded() {
        Properties base = load("application.yaml");

        assertThat(base.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(base.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
                .isEqualTo("20s");
    }

    @Test
    void everyExternalCallHasATimeout() {
        Properties base = load("application.yaml");

        assertThat(base.getProperty("spring.datasource.hikari.connection-timeout"))
                .isEqualTo("5000");
        assertThat(base.getProperty("spring.jdbc.template.query-timeout")).isEqualTo("15s");
        for (String file : new String[] {"application-local.yaml", "application-prod.yaml"}) {
            Properties profile = load(file);
            for (String key : new String[] {"connectiontimeout", "timeout", "writetimeout"}) {
                assertThat(profile.getProperty("spring.mail.properties.mail.smtp." + key))
                        .as(file + " " + key)
                        .isEqualTo("5000");
            }
        }
    }

    @Test
    void readinessDependsOnTheDatabaseAndSqlErrorLoggingIsOff() {
        Properties base = load("application.yaml");

        assertThat(base.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db");
        assertThat(
                        base.getProperty(
                                "logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper"))
                .isEqualTo("OFF");
        assertThat(base.getProperty("management.endpoint.health.probes.enabled")).isEqualTo("true");
        assertThat(base.getProperty("logging.level.org.hibernate.orm.jdbc.error")).isEqualTo("OFF");
        assertThat(
                        base.getProperty(
                                "logging.level.org.springframework.boot.health.contributor.AbstractHealthIndicator"))
                .isEqualTo("ERROR");
    }
}
