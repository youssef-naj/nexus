package com.l2c.nexus.request.persistence;

import com.l2c.nexus.request.domain.ServiceRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    /** Always scoped by organization: an id from another tenant simply is not found. */
    Optional<ServiceRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
