package com.l2c.nexus.department.application;

import com.l2c.nexus.department.domain.Department;
import com.l2c.nexus.department.persistence.DepartmentRepository;
import com.l2c.nexus.shared.error.ConflictException;
import com.l2c.nexus.shared.error.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentDirectory {

    private final DepartmentRepository departments;

    public DepartmentDirectory(DepartmentRepository departments) {
        this.departments = departments;
    }

    /**
     * Confirms the department exists in this organization and is active, and holds a shared lock on
     * it until the caller's transaction ends, so it cannot be deactivated in between.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActiveForUse(UUID organizationId, UUID departmentId) {
        Department department =
                departments
                        .lockSharedByIdAndOrganizationId(departmentId, organizationId)
                        .orElseThrow(NotFoundException::new);
        if (!department.isActive()) {
            throw new ConflictException(
                    "DEPARTMENT_INACTIVE", "This department is inactive and cannot be used.");
        }
    }
}
