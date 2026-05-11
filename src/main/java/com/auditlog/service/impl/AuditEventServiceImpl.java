package com.auditlog.service.impl;

import com.auditlog.AuditEventQueryCursor;
import com.auditlog.InvalidQueryException;
import com.auditlog.dao.entity.AuditEventEntity;
import com.auditlog.dao.repository.AuditEventQueryRepository;
import com.auditlog.dao.repository.AuditEventRepository;
import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.request.SearchAuditEventsRequest;
import com.auditlog.dto.response.AuditEventResponse;
import com.auditlog.dto.response.AuditEventSearchItemResponse;
import com.auditlog.dto.response.SearchAuditEventsResponse;
import com.auditlog.service.AuditEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditEventServiceImpl implements AuditEventService {

    private static final int CURSOR_VERSION = 1;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 50;
    private static final Duration CURSOR_TTL = Duration.ofHours(1);
    private static final Pattern DATE_ONLY_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final AuditEventRepository auditEventRepository;
    private final AuditEventQueryRepository auditEventQueryRepository;
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
        String filterFingerprint = buildFilterFingerprint(actor, resource, from, to);
        AuditEventQueryCursor cursor = decodeCursor(request.cursor(), filterFingerprint, limit);

        List<AuditEventEntity> entities = auditEventQueryRepository.search(
                actor,
                resource,
                from,
                to,
                cursor == null ? null : cursor.lastTimestamp(),
                cursor == null ? null : cursor.lastId(),
                limit + 1);

        boolean hasMore = entities.size() > limit;
        List<AuditEventEntity> pageEntities = hasMore ? entities.subList(0, limit) : entities;
        List<AuditEventSearchItemResponse> items =
                pageEntities.stream().map(this::toSearchResponse).toList();

        String nextCursor =
                hasMore ? encodeCursor(pageEntities.get(pageEntities.size() - 1), filterFingerprint, limit) : null;
        return new SearchAuditEventsResponse(items, nextCursor);
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
        return parseTimestamp(
                rawFrom, true, "INVALID_FROM", "The from parameter must be a valid UTC timestamp or date.");
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
                return date.plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .minusNanos(1_000)
                        .toInstant();
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

    private AuditEventQueryCursor decodeCursor(String rawCursor, String expectedFingerprint, int expectedLimit) {
        if (rawCursor == null) {
            return null;
        }

        String cursor = rawCursor.trim();
        if (cursor.isEmpty()) {
            throw invalidCursor();
        }

        AuditEventQueryCursor payload;
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            payload = objectMapper.readValue(json, AuditEventQueryCursor.class);
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            throw invalidCursor();
        }

        if (payload == null
                || payload.version() != CURSOR_VERSION
                || payload.issuedAt() == null
                || payload.lastTimestamp() == null
                || payload.lastId() == null
                || payload.filterFingerprint() == null
                || payload.limit() == null) {
            throw invalidCursor();
        }

        if (payload.issuedAt().isBefore(Instant.now().minus(CURSOR_TTL))) {
            throw invalidCursor();
        }

        if (!expectedFingerprint.equals(payload.filterFingerprint()) || payload.limit() != expectedLimit) {
            throw invalidCursor();
        }

        return payload;
    }

    private String encodeCursor(AuditEventEntity lastEntity, String filterFingerprint, int limit) {
        AuditEventQueryCursor payload = new AuditEventQueryCursor(
                CURSOR_VERSION, Instant.now(), lastEntity.getTimestamp(), lastEntity.getId(), filterFingerprint, limit);

        try {
            String json = objectMapper.writeValueAsString(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to encode pagination cursor.", exception);
        }
    }

    private String buildFilterFingerprint(String actor, String resource, Instant from, Instant to) {
        return "actor=" + nullToken(actor)
                + "|resource=" + nullToken(resource)
                + "|from=" + nullToken(from)
                + "|to=" + nullToken(to);
    }

    private String nullToken(Object value) {
        return value == null ? "<null>" : value.toString();
    }

    private InvalidQueryException invalidLimit() {
        return new InvalidQueryException(
                "INVALID_LIMIT", "The limit parameter must be greater than 0 and less than or equal to 50.");
    }

    private InvalidQueryException invalidCursor() {
        return new InvalidQueryException("INVALID_CURSOR", "The cursor is invalid or has expired.");
    }
}
