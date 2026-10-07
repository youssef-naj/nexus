package com.l2c.nexus.department.persistence;

import com.l2c.nexus.department.domain.Department;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    /** Always scoped by organization: an id from another tenant simply is not found. */
    Optional<Department> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** True if another department of this organization already has the name (any case). */
    @Query(
            "select count(d) > 0 from Department d where d.organizationId = :org"
                    + " and lower(d.name) = lower(:name) and d.id <> :excludeId")
    boolean nameTaken(
            @Param("org") UUID organizationId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);

    /** SELECT ... FOR UPDATE, scoped by organization. The lock lasts until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.id = :id and d.organizationId = :org")
    Optional<Department> lockByIdAndOrganizationId(
            @Param("id") UUID id, @Param("org") UUID organizationId);

    /** SELECT ... FOR SHARE: many readers at once, but it blocks a concurrent deactivation. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from Department d where d.id = :id and d.organizationId = :org")
    Optional<Department> lockSharedByIdAndOrganizationId(
            @Param("id") UUID id, @Param("org") UUID organizationId);
}
