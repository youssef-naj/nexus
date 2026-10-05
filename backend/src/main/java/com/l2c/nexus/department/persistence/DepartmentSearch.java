package com.l2c.nexus.department.persistence;

import com.l2c.nexus.department.domain.Department;
import com.l2c.nexus.department.domain.DepartmentSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * The department listing. The query text is assembled only from fixed fragments (the sort comes
 * from a whitelist enum); every user-supplied value is a bound parameter.
 */
@Repository
public class DepartmentSearch {

    public record Criteria(Boolean active, String query, DepartmentSort sort, boolean ascending) {}

    public record Result(List<Department> items, long total) {}

    @PersistenceContext private EntityManager entityManager;

    public Result search(UUID organizationId, Criteria criteria, int page, int size) {
        StringBuilder where = new StringBuilder("d.organizationId = :org");
        if (criteria.active() != null) {
            where.append(" and d.active = :active");
        }
        if (criteria.query() != null) {
            where.append(" and lower(d.name) like :pattern escape '\\'");
        }
        String column = criteria.sort() == DepartmentSort.CREATED ? "d.createdAt" : "lower(d.name)";
        String order = column + (criteria.ascending() ? " asc" : " desc") + ", d.id asc";

        TypedQuery<Department> items =
                entityManager.createQuery(
                        "select d from Department d where " + where + " order by " + order,
                        Department.class);
        TypedQuery<Long> count =
                entityManager.createQuery(
                        "select count(d) from Department d where " + where, Long.class);
        for (TypedQuery<?> query : List.of(items, count)) {
            query.setParameter("org", organizationId);
            if (criteria.active() != null) {
                query.setParameter("active", criteria.active());
            }
            if (criteria.query() != null) {
                query.setParameter(
                        "pattern",
                        "%" + escapeLike(criteria.query().toLowerCase(Locale.ROOT)) + "%");
            }
        }
        items.setFirstResult(page * size);
        items.setMaxResults(size);
        return new Result(items.getResultList(), count.getSingleResult());
    }

    /** Makes %, _ and \ match themselves, so a search for "100%" does not match everything. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
