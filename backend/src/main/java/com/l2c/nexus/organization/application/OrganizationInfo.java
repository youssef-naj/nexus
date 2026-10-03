package com.l2c.nexus.organization.application;

import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.UUID;

public record OrganizationInfo(UUID id, String name, String slug, OrganizationStatus status) {}
