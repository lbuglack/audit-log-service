package com.auditlog.service.impl;

import com.auditlog.dao.entity.AuditEventEntity;
import com.auditlog.dao.repository.AuditEventRepository;
import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.request.SearchAuditEventsRequest;
import com.auditlog.dto.response.AuditEventResponse;
import com.auditlog.dto.response.AuditEventSearchItemResponse;
import com.auditlog.dto.response.SearchAuditEventsResponse;
import com.auditlog.service.AuditEventService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditEventServiceImpl implements AuditEventService {

    private final AuditEventRepository auditEventRepository;

    @Override
    @Transactional
    public AuditEventResponse create(CreateAuditEventRequest request) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setTimestamp(Instant.now());
        entity.setActor(request.actor());
        entity.setAction(request.action());
        entity.setResource(request.resource());
        entity.setOutcome(request.outcome());
        entity.setContext(request.context());

        return toResponse(auditEventRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public SearchAuditEventsResponse search(SearchAuditEventsRequest request) {
        Instant from = request.from() == null ? null : Instant.parse(request.from());
        Instant to = request.to() == null ? null : Instant.parse(request.to());

        Specification<AuditEventEntity> specification = (root, query, criteriaBuilder) -> {
            query.orderBy(criteriaBuilder.desc(root.get("timestamp")));
            List<Predicate> predicates = new ArrayList<>();
            if (request.actor() != null) {
                predicates.add(criteriaBuilder.equal(root.get("actor"), request.actor()));
            }
            if (request.resource() != null) {
                predicates.add(criteriaBuilder.equal(root.get("resource"), request.resource()));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        List<AuditEventSearchItemResponse> items =
                auditEventRepository.findAll(specification).stream().map(this::toSearchResponse).toList();
        return new SearchAuditEventsResponse(items, null);
    }

    private AuditEventResponse toResponse(AuditEventEntity entity) {
        return new AuditEventResponse(
                entity.getId(),
                entity.getTimestamp(),
                entity.getActor(),
                entity.getAction(),
                entity.getResource(),
                entity.getOutcome(),
                entity.getContext());
    }

    private AuditEventSearchItemResponse toSearchResponse(AuditEventEntity entity) {
        return new AuditEventSearchItemResponse(
                entity.getId(),
                entity.getTimestamp(),
                entity.getActor(),
                entity.getAction(),
                entity.getResource(),
                entity.getOutcome(),
                entity.getContext());
    }
}
