package com.l2c.nexus.identity;

import com.l2c.nexus.identity.application.AuthRateLimits;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthRateLimits.class)
class IdentityConfig {}
