package com.auditlog.dao.repository;

import com.auditlog.dao.entity.AuditEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditEventQueryRepository {

    private final EntityManager entityManager;

    public List<AuditEventEntity> search(
            List<String> actors,
            String resource,
            Instant from,
            Instant to,
            Instant cursorTimestamp,
            UUID cursorId,
            int limit) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditEventEntity> criteriaQuery = criteriaBuilder.createQuery(AuditEventEntity.class);
        Root<AuditEventEntity> root = criteriaQuery.from(AuditEventEntity.class);
        Path<String> actorPath = root.get("actor");
        Path<String> resourcePath = root.get("resource");
        Path<Instant> timestampPath = root.get("timestamp");
        Path<UUID> idPath = root.get("id");

        List<Predicate> predicates = new ArrayList<>();
        if (actors != null && !actors.isEmpty()) {
            predicates.add(criteriaBuilder.lower(actorPath).in(actors));
        }
        if (resource != null) {
            predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(resourcePath), resource));
        }
        if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(timestampPath, from));
        }
        if (to != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(timestampPath, to));
        }
        if (cursorTimestamp != null && cursorId != null) {
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.lessThan(timestampPath, cursorTimestamp),
                    criteriaBuilder.and(
                            criteriaBuilder.equal(timestampPath, cursorTimestamp),
                            criteriaBuilder.lessThan(idPath, cursorId))));
        }

        criteriaQuery
                .select(root)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(criteriaBuilder.desc(timestampPath), criteriaBuilder.desc(idPath));

        TypedQuery<AuditEventEntity> query = entityManager.createQuery(criteriaQuery);
        query.setMaxResults(limit);
        return query.getResultList();
    }
}
