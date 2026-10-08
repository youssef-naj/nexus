package com.l2c.nexus.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** An internal service request. Workflow transitions arrive with the approval phase. */
@Entity
@Table(name = "service_requests")
public class ServiceRequest {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 20, updatable = false)
    private String reference;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestStatus status;

    @Column(name = "created_by_membership_id", nullable = false, updatable = false)
    private UUID createdByMembershipId;

    @Column(name = "assignee_membership_id")
    private UUID assigneeMembershipId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    /** Required by JPA. Use {@link #draft}. */
    protected ServiceRequest() {}

    public static ServiceRequest draft(
            UUID organizationId,
            String reference,
            String title,
            String description,
            RequestCategory category,
            UUID createdByMembershipId,
            UUID departmentId,
            LocalDate dueDate,
            Instant now) {
        ServiceRequest request = new ServiceRequest();
        request.organizationId = organizationId;
        request.reference = reference;
        request.title = title;
        request.description = description;
        request.category = category;
        request.status = RequestStatus.DRAFT;
        request.createdByMembershipId = createdByMembershipId;
        request.departmentId = departmentId;
        request.dueDate = dueDate;
        request.createdAt = now;
        request.updatedAt = now;
        return request;
    }

    public void edit(
            String newTitle,
            String newDescription,
            RequestCategory newCategory,
            UUID newDepartmentId,
            LocalDate newDueDate,
            Instant now) {
        this.title = newTitle;
        this.description = newDescription;
        this.category = newCategory;
        this.departmentId = newDepartmentId;
        this.dueDate = newDueDate;
        this.updatedAt = now;
    }

    public void applyTransition(RequestAction action, Instant now) {
        if (!action.allowedFrom(status)) {
            throw new IllegalStateException("Cannot " + action + " a request that is " + status);
        }
        this.status = action.target();
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getReference() {
        return reference;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public RequestCategory getCategory() {
        return category;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public UUID getCreatedByMembershipId() {
        return createdByMembershipId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public long getVersion() {
        return version;
    }
}
