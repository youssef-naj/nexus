package com.l2c.nexus.organization.application;

import com.l2c.nexus.organization.domain.OrganizationStatus;
import com.l2c.nexus.shared.sql.LikeEscaper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A read-only catalog of organizations for the platform: name, slug, status, creation date and an
 * active member count, nothing else (ADR-0006, ADR-0032). The member count joins memberships,
 * covered by the documented read-model exception. Text search is bound as a parameter and its
 * wildcards match themselves; the sort comes from a whitelist.
 */
@Service
public class OrganizationCatalog {

    public enum Sort {
        NAME,
        CREATED
    }

    public record Criteria(OrganizationStatus status, String query, Sort sort, boolean ascending) {}

    public record Page(List<OrganizationAdminView> items, long total) {}

    private static final int MAX_QUERY_LENGTH = 80;

    private static final String COLUMNS =
            "o.id, o.name, o.slug, o.status, o.created_at, o.version,"
                    + " (SELECT count(*) FROM memberships m WHERE m.organization_id = o.id"
                    + " AND m.status = 'ACTIVE') AS member_count";

    private final JdbcClient jdbc;

    public OrganizationCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page search(Criteria criteria, int page, int size) {
        StringBuilder where = new StringBuilder("true");
        List<Object> params = new ArrayList<>();
        if (criteria.status() != null) {
            where.append(" AND o.status = ?");
            params.add(criteria.status().name());
        }
        String query = criteria.query() == null ? "" : criteria.query().trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        if (!query.isEmpty()) {
            where.append(" AND (lower(o.name) LIKE ? ESCAPE '\\' OR o.slug LIKE ? ESCAPE '\\')");
            String pattern = "%" + LikeEscaper.escape(query.toLowerCase(Locale.ROOT)) + "%";
            params.add(pattern);
            params.add(pattern);
        }

        long total =
                jdbc.sql("SELECT count(*) FROM organizations o WHERE " + where)
                        .params(params)
                        .query(Long.class)
                        .single();

        String direction = criteria.ascending() ? "ASC" : "DESC";
        String order =
                criteria.sort() == Sort.CREATED
                        ? "o.created_at " + direction
                        : "lower(o.name) " + direction;
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<OrganizationAdminView> items =
                jdbc.sql(
                                "SELECT "
                                        + COLUMNS
                                        + " FROM organizations o WHERE "
                                        + where
                                        + " ORDER BY "
                                        + order
                                        + ", o.id ASC LIMIT ? OFFSET ?")
                        .params(pageParams)
                        .query((rs, rowNum) -> map(rs))
                        .list();
        return new Page(items, total);
    }

    @Transactional(readOnly = true)
    public Optional<OrganizationAdminView> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM organizations o WHERE o.id = ?")
                .param(id)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    private static OrganizationAdminView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrganizationAdminView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                OrganizationStatus.valueOf(rs.getString("status")),
                rs.getLong("member_count"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }
}
