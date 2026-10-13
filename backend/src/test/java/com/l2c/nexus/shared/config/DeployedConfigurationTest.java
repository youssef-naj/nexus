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
    void theProdProfileForcesSecureCookies() {
        Properties prod = load("application-prod.yaml");

        assertThat(prod.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(prod.getProperty("nexus.security.secure-cookies")).isEqualTo("true");
    }

    @Test
    void apiDocumentationIsOffByDefaultAndSessionsNeverTravelInUrls() {
        Properties base = load("application.yaml");

        assertThat(base.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(base.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(base.getProperty("server.servlet.session.tracking-modes")).isEqualTo("cookie");
        assertThat(base.getProperty("nexus.security.secure-cookies")).isEqualTo("false");
    }
}
