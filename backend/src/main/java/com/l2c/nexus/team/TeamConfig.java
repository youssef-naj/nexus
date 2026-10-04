package com.l2c.nexus.team;

import com.l2c.nexus.team.application.TeamRateLimits;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TeamRateLimits.class)
class TeamConfig {}
