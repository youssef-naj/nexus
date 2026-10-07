package com.l2c.nexus.request;

import com.l2c.nexus.request.application.RequestRateLimits;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RequestRateLimits.class)
class RequestConfig {}
