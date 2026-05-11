package com.auditlog.service.impl;

import com.auditlog.InvalidQueryException;
import com.auditlog.dao.entity.AuditEventEntity;
import com.auditlog.dao.repository.AuditEventRepository;
import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.request.SearchAuditEventsRequest;
import com.auditlog.dto.response.AuditEventResponse;
import com.auditlog.dto.response.AuditEventSearchItemResponse;
import com.auditlog.dto.response.SearchAuditEventsResponse;
import com.auditlog.service.AuditEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditEventServiceImpl implements AuditEventService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 50;
    private static final Duration CURSOR_TTL = Duration.ofHours(1);
    private static final Pattern DATE_ONLY_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

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
        String actor = normalizeFilter(request.actor());
        String resource = normalizeFilter(request.resource());
        Instant from = parseFrom(request.from());
        Instant to = parseTo(request.to());
        validateTimeRange(from, to);
        int limit = parseLimit(request.limit());
        validateCursor(request.cursor());

        Specification<AuditEventEntity> specification = (root, query, criteriaBuilder) -> {
            query.orderBy(criteriaBuilder.desc(root.get("timestamp")));
            List<Predicate> predicates = new ArrayList<>();
            if (actor != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("actor")), actor));
            }
            if (resource != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("resource")), resource));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        List<AuditEventSearchItemResponse> items = auditEventRepository
                .findAll(specification, PageRequest.of(0, limit))
                .stream()
                .map(this::toSearchResponse)
                .toList();

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

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.toLowerCase();
    }

    private Instant parseFrom(String rawFrom) {
        return parseTimestamp(rawFrom, true, "INVALID_FROM", "The from parameter must be a valid UTC timestamp or date.");
    }

    private Instant parseTo(String rawTo) {
        return parseTimestamp(rawTo, false, "INVALID_TO", "The to parameter must be a valid UTC timestamp or date.");
    }

    private Instant parseTimestamp(String rawValue, boolean startOfDay, String code, String message) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw new InvalidQueryException(code, message);
        }

        if (DATE_ONLY_PATTERN.matcher(value).matches()) {
            try {
                LocalDate date = LocalDate.parse(value);
                if (startOfDay) {
                    return date.atStartOfDay(ZoneOffset.UTC).toInstant();
                }
                return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).minusNanos(1_000).toInstant();
            } catch (DateTimeParseException exception) {
                throw new InvalidQueryException(code, message);
            }
        }

        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(value);
            if (!ZoneOffset.UTC.equals(timestamp.getOffset())) {
                throw new InvalidQueryException(code, message);
            }
            return timestamp.toInstant();
        } catch (DateTimeParseException exception) {
            throw new InvalidQueryException(code, message);
        }
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidQueryException(
                    "INVALID_TIME_RANGE", "The from parameter must be earlier than or equal to the to parameter.");
        }
    }

    private int parseLimit(String rawLimit) {
        if (rawLimit == null) {
            return DEFAULT_LIMIT;
        }

        String value = rawLimit.trim();
        if (value.isEmpty()) {
            throw invalidLimit();
        }

        try {
            int limit = Integer.parseInt(value);
            if (limit <= 0 || limit > MAX_LIMIT) {
                throw invalidLimit();
            }
            return limit;
        } catch (NumberFormatException exception) {
            throw invalidLimit();
        }
    }

    private void validateCursor(String rawCursor) {
        if (rawCursor == null) {
            return;
        }

        String cursor = rawCursor.trim();
        if (cursor.isEmpty()) {
            throw invalidCursor();
        }

        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(json);
            JsonNode issuedAtNode = payload.get("issuedAt");
            if (issuedAtNode == null || !issuedAtNode.isTextual()) {
                throw invalidCursor();
            }

            Instant issuedAt = Instant.parse(issuedAtNode.asText());
            if (issuedAt.isBefore(Instant.now().minus(CURSOR_TTL))) {
                throw invalidCursor();
            }
        } catch (InvalidQueryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidCursor();
        }
    }

    private InvalidQueryException invalidLimit() {
        return new InvalidQueryException(
                "INVALID_LIMIT", "The limit parameter must be greater than 0 and less than or equal to 50.");
    }

    private InvalidQueryException invalidCursor() {
        return new InvalidQueryException("INVALID_CURSOR", "The cursor is invalid or has expired.");
    }
}
