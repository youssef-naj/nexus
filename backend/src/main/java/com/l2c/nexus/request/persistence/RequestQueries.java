package com.l2c.nexus.request.persistence;

import com.l2c.nexus.request.domain.RequestCategory;
import com.l2c.nexus.request.domain.RequestSort;
import com.l2c.nexus.request.domain.RequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The request read model: list and detail, joined with the names of the creator, department and
 * assignee. Read-only; the joins over memberships, users and departments are the documented
 * exception to "modules do not read each other's tables" (ADR-0022, ADR-0026). The SQL text is
 * assembled only from fixed fragments (the sort comes from a whitelist enum); every user-supplied
 * value is a bound parameter. Every statement is scoped by organization.
 */
@Repository
public class RequestQueries {

    public record Filter(
            RequestStatus status,
            RequestCategory category,
            UUID departmentId,
            UUID creatorMembershipId,
            String query,
            Instant createdFrom,
            Instant createdBefore) {}

    public record SummaryPage(List<RequestSummary> items, long total) {}

    private static final String JOINS =
            " FROM service_requests r"
                    + " JOIN memberships cm ON cm.organization_id = r.organization_id"
                    + " AND cm.id = r.created_by_membership_id"
                    + " JOIN users cu ON cu.id = cm.user_id"
                    + " LEFT JOIN departments d ON d.organization_id = r.organization_id"
                    + " AND d.id = r.department_id";

    private static final String DETAIL_JOINS =
            JOINS
                    + " LEFT JOIN memberships am ON am.organization_id = r.organization_id"
                    + " AND am.id = r.assignee_membership_id"
                    + " LEFT JOIN users au ON au.id = am.user_id";

    private static final String SUMMARY_COLUMNS =
            "r.id, r.reference, r.title, r.category, r.status, r.created_by_membership_id,"
                    + " cu.display_name AS created_by_name, r.department_id,"
                    + " d.name AS department_name, r.due_date, r.created_at, r.updated_at,"
                    + " r.version";

    private static final String DETAIL_COLUMNS =
            "r.id, r.reference, r.title, r.description, r.category, r.status,"
                    + " r.created_by_membership_id, cu.display_name AS created_by_name,"
                    + " r.assignee_membership_id, au.display_name AS assignee_name,"
                    + " r.department_id, d.name AS department_name, r.due_date, r.created_at,"
                    + " r.updated_at, r.version";

    private final JdbcClient jdbc;

    public RequestQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public SummaryPage search(
            UUID organizationId,
            Filter filter,
            RequestSort sort,
            boolean ascending,
            int page,
            int size) {
        StringBuilder where = new StringBuilder("r.organization_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(organizationId);
        if (filter.status() != null) {
            where.append(" AND r.status = ?");
            params.add(filter.status().name());
        }
        if (filter.category() != null) {
            where.append(" AND r.category = ?");
            params.add(filter.category().name());
        }
        if (filter.departmentId() != null) {
            where.append(" AND r.department_id = ?");
            params.add(filter.departmentId());
        }
        if (filter.creatorMembershipId() != null) {
            where.append(" AND r.created_by_membership_id = ?");
            params.add(filter.creatorMembershipId());
        }
        if (filter.query() != null) {
            where.append(
                    " AND (lower(r.title) LIKE ? ESCAPE '\\' OR lower(r.reference) LIKE ? ESCAPE '\\')");
            String pattern = "%" + escapeLike(filter.query().toLowerCase(Locale.ROOT)) + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (filter.createdFrom() != null) {
            where.append(" AND r.created_at >= ?");
            params.add(OffsetDateTime.ofInstant(filter.createdFrom(), ZoneOffset.UTC));
        }
        if (filter.createdBefore() != null) {
            where.append(" AND r.created_at < ?");
            params.add(OffsetDateTime.ofInstant(filter.createdBefore(), ZoneOffset.UTC));
        }

        long total =
                jdbc.sql("SELECT count(*) FROM service_requests r WHERE " + where)
                        .params(params)
                        .query(Long.class)
                        .single();

        String direction = ascending ? "ASC" : "DESC";
        String order =
                switch (sort) {
                    case CREATED -> "r.created_at " + direction;
                    case UPDATED -> "r.updated_at " + direction;
                    case DUE_DATE -> "r.due_date " + direction + " NULLS LAST";
                };
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<RequestSummary> items =
                jdbc.sql(
                                "SELECT "
                                        + SUMMARY_COLUMNS
                                        + JOINS
                                        + " WHERE "
                                        + where
                                        + " ORDER BY "
                                        + order
                                        + ", r.id ASC LIMIT ? OFFSET ?")
                        .params(pageParams)
                        .query(
                                (rs, rowNum) ->
                                        new RequestSummary(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("reference"),
                                                rs.getString("title"),
                                                RequestCategory.valueOf(rs.getString("category")),
                                                RequestStatus.valueOf(rs.getString("status")),
                                                rs.getObject(
                                                        "created_by_membership_id", UUID.class),
                                                rs.getString("created_by_name"),
                                                rs.getObject("department_id", UUID.class),
                                                rs.getString("department_name"),
                                                rs.getObject("due_date", LocalDate.class),
                                                rs.getObject("created_at", OffsetDateTime.class)
                                                        .toInstant(),
                                                rs.getObject("updated_at", OffsetDateTime.class)
                                                        .toInstant(),
                                                rs.getLong("version")))
                        .list();
        return new SummaryPage(items, total);
    }

    /**
     * The request, if it exists in this organization (and, when given, was created by that member).
     */
    public Optional<RequestDetail> find(
            UUID organizationId, UUID requestId, UUID onlyCreatedByMembershipId) {
        String restriction =
                onlyCreatedByMembershipId == null ? "" : " AND r.created_by_membership_id = ?";
        List<Object> params = new ArrayList<>(List.of(organizationId, requestId));
        if (onlyCreatedByMembershipId != null) {
            params.add(onlyCreatedByMembershipId);
        }
        return jdbc.sql(
                        "SELECT "
                                + DETAIL_COLUMNS
                                + DETAIL_JOINS
                                + " WHERE r.organization_id = ? AND r.id = ?"
                                + restriction)
                .params(params)
                .query(
                        (rs, rowNum) ->
                                new RequestDetail(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("reference"),
                                        rs.getString("title"),
                                        rs.getString("description"),
                                        RequestCategory.valueOf(rs.getString("category")),
                                        RequestStatus.valueOf(rs.getString("status")),
                                        rs.getObject("created_by_membership_id", UUID.class),
                                        rs.getString("created_by_name"),
                                        rs.getObject("assignee_membership_id", UUID.class),
                                        rs.getString("assignee_name"),
                                        rs.getObject("department_id", UUID.class),
                                        rs.getString("department_name"),
                                        rs.getObject("due_date", LocalDate.class),
                                        rs.getObject("created_at", OffsetDateTime.class)
                                                .toInstant(),
                                        rs.getObject("updated_at", OffsetDateTime.class)
                                                .toInstant(),
                                        rs.getLong("version")))
                .optional();
    }

    /** Makes %, _ and \ match themselves, so a search for "100%" does not match everything. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
