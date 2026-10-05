package com.l2c.nexus.department.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A grouping inside one organization. Deactivated, never deleted, so history stays valid. */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    /** Required by JPA. Use {@link #create}. */
    protected Department() {}

    public static Department create(
            UUID organizationId, String name, String description, Instant now) {
        Department department = new Department();
        department.organizationId = organizationId;
        department.name = name;
        department.description = description;
        department.active = true;
        department.createdAt = now;
        department.updatedAt = now;
        return department;
    }

    public void update(String newName, String newDescription, Instant now) {
        this.name = newName;
        this.description = newDescription;
        this.updatedAt = now;
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public void reactivate(Instant now) {
        this.active = true;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
