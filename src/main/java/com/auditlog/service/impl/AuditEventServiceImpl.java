package com.auditlog.service.impl;

import com.auditlog.dao.entity.AuditEventEntity;
import com.auditlog.dao.repository.AuditEventRepository;
import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.response.AuditEventResponse;
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
    public List<AuditEventResponse> search(String actor, String resource, Instant from, Instant to) {
        Specification<AuditEventEntity> spec = (root, query, cb) -> {
            query.orderBy(cb.desc(root.get("timestamp")));
            List<Predicate> predicates = new ArrayList<>();
            if (actor != null) predicates.add(cb.equal(root.get("actor"), actor));
            if (resource != null) predicates.add(cb.equal(root.get("resource"), resource));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditEventRepository.findAll(spec).stream().map(this::toResponse).toList();
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
}
