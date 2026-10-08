package com.l2c.nexus.request.persistence;

import com.l2c.nexus.request.domain.ServiceRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    /** Always scoped by organization: an id from another tenant simply is not found. */
    Optional<ServiceRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** SELECT ... FOR UPDATE, scoped by organization: decisions on one request queue up. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.id = :id and r.organizationId = :org")
    Optional<ServiceRequest> lockByIdAndOrganizationId(
            @Param("id") UUID id, @Param("org") UUID organizationId);
}
