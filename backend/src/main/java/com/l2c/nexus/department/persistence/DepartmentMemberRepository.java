package com.l2c.nexus.department.persistence;

import com.l2c.nexus.membership.domain.OrgRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Department assignments. Plain SQL: the table has a composite key and no entity of its own. The
 * member listing is a read-only join over memberships and users, the documented exception to
 * "modules do not read each other's tables" (ADR-0022, ADR-0025). Every statement is scoped by
 * organization.
 */
@Repository
public class DepartmentMemberRepository {

    public record MemberRow(
            UUID membershipId,
            UUID userId,
            String displayName,
            String email,
            OrgRole role,
            Instant assignedAt) {}

    public record MemberRowPage(List<MemberRow> items, long total) {}

    public record DepartmentRef(UUID id, String name, boolean active) {}

    private final JdbcClient jdbc;

    public DepartmentMemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns true if the member was newly assigned, false if they already were. */
    public boolean add(UUID organizationId, UUID departmentId, UUID membershipId, Instant now) {
        return jdbc.sql(
                                "INSERT INTO department_memberships (organization_id, department_id,"
                                        + " membership_id, created_at) VALUES (?, ?, ?, ?)"
                                        + " ON CONFLICT (department_id, membership_id) DO NOTHING")
                        .params(
                                organizationId,
                                departmentId,
                                membershipId,
                                OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                        .update()
                == 1;
    }

    /** Returns true if an assignment existed and was removed. */
    public boolean remove(UUID organizationId, UUID departmentId, UUID membershipId) {
        return jdbc.sql(
                                "DELETE FROM department_memberships WHERE organization_id = ?"
                                        + " AND department_id = ? AND membership_id = ?")
                        .params(organizationId, departmentId, membershipId)
                        .update()
                == 1;
    }

    public int removeAllFor(UUID organizationId, UUID membershipId) {
        return jdbc.sql(
                        "DELETE FROM department_memberships WHERE organization_id = ?"
                                + " AND membership_id = ?")
                .params(organizationId, membershipId)
                .update();
    }

    public MemberRowPage members(
            UUID organizationId, UUID departmentId, boolean includeEmail, int page, int size) {
        String from =
                " FROM department_memberships dm"
                        + " JOIN memberships m ON m.organization_id = dm.organization_id"
                        + " AND m.id = dm.membership_id"
                        + " JOIN users u ON u.id = m.user_id"
                        + " WHERE dm.organization_id = ? AND dm.department_id = ?"
                        + " AND m.status = 'ACTIVE'";
        long total =
                jdbc.sql("SELECT count(*)" + from)
                        .params(organizationId, departmentId)
                        .query(Long.class)
                        .single();
        List<MemberRow> items =
                jdbc.sql(
                                "SELECT m.id AS membership_id, m.user_id, u.display_name, u.email,"
                                        + " m.role, dm.created_at"
                                        + from
                                        + " ORDER BY lower(u.display_name), m.id LIMIT ? OFFSET ?")
                        .params(organizationId, departmentId, size, (long) page * size)
                        .query(
                                (rs, rowNum) ->
                                        new MemberRow(
                                                rs.getObject("membership_id", UUID.class),
                                                rs.getObject("user_id", UUID.class),
                                                rs.getString("display_name"),
                                                includeEmail ? rs.getString("email") : null,
                                                OrgRole.valueOf(rs.getString("role")),
                                                rs.getObject("created_at", OffsetDateTime.class)
                                                        .toInstant()))
                        .list();
        return new MemberRowPage(items, total);
    }

    public List<DepartmentRef> departmentsOf(UUID organizationId, UUID membershipId) {
        return jdbc.sql(
                        "SELECT d.id, d.name, d.active FROM department_memberships dm"
                                + " JOIN departments d ON d.organization_id = dm.organization_id"
                                + " AND d.id = dm.department_id"
                                + " WHERE dm.organization_id = ? AND dm.membership_id = ?"
                                + " ORDER BY lower(d.name), d.id")
                .params(organizationId, membershipId)
                .query(
                        (rs, rowNum) ->
                                new DepartmentRef(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("name"),
                                        rs.getBoolean("active")))
                .list();
    }
}
