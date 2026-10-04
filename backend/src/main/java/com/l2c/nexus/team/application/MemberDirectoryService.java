package com.l2c.nexus.team.application;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.organization.application.AccessPolicy;
import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.application.Permission;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The member list: a read-only join over memberships and users, because sorting by name needs both.
 * This is the one deliberate exception to "modules never read each other's tables" (ADR-0022): it
 * only reads, and every write still goes through the owning module's service.
 */
@Service
public class MemberDirectoryService {

    public record MemberListItem(
            UUID membershipId,
            UUID userId,
            String displayName,
            String email,
            OrgRole role,
            Instant joinedAt,
            long version) {}

    public record MemberPage(List<MemberListItem> items, long total) {}

    private final JdbcClient jdbc;
    private final AccessPolicy policy;

    public MemberDirectoryService(JdbcClient jdbc, AccessPolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public MemberPage list(OrgContext org, OrgRole roleFilter, int page, int size) {
        policy.require(org, Permission.MEMBER_VIEW);
        // Email addresses are personal data: only roles that manage membership see them
        boolean showEmails = policy.can(org.role(), Permission.MEMBER_INVITE);

        String where =
                "m.organization_id = ? AND m.status = 'ACTIVE'"
                        + (roleFilter == null ? "" : " AND m.role = ?");
        List<Object> params = new ArrayList<>();
        params.add(org.organizationId());
        if (roleFilter != null) {
            params.add(roleFilter.name());
        }

        long total =
                jdbc.sql("SELECT count(*) FROM memberships m WHERE " + where)
                        .params(params)
                        .query(Long.class)
                        .single();

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<MemberListItem> items =
                jdbc.sql(
                                "SELECT m.id, m.user_id, u.display_name, u.email, m.role,"
                                        + " m.created_at, m.version"
                                        + " FROM memberships m JOIN users u ON u.id = m.user_id"
                                        + " WHERE "
                                        + where
                                        + " ORDER BY lower(u.display_name), m.id LIMIT ? OFFSET ?")
                        .params(pageParams)
                        .query(
                                (rs, rowNum) ->
                                        new MemberListItem(
                                                rs.getObject("id", UUID.class),
                                                rs.getObject("user_id", UUID.class),
                                                rs.getString("display_name"),
                                                showEmails ? rs.getString("email") : null,
                                                OrgRole.valueOf(rs.getString("role")),
                                                rs.getObject("created_at", OffsetDateTime.class)
                                                        .toInstant(),
                                                rs.getLong("version")))
                        .list();
        return new MemberPage(items, total);
    }
}
