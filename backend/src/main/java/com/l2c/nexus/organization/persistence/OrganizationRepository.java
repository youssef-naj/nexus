package com.l2c.nexus.organization.persistence;

import com.l2c.nexus.organization.domain.Organization;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsBySlug(String slug);

    /** Takes a row lock (SELECT ... FOR UPDATE) that is held until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> lockById(@Param("id") UUID id);

    /** SELECT ... FOR SHARE: many readers at once, but it blocks a concurrent role change. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> lockSharedById(@Param("id") UUID id);
}
