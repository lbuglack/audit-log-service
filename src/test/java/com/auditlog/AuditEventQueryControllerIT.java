package com.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.auditlog.dto.response.ApiErrorResponse;
import com.auditlog.dto.response.SearchAuditEventsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditEventQueryControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("ALTER TABLE audit_events DISABLE TRIGGER ALL");
        jdbcTemplate.execute("TRUNCATE audit_events");
        jdbcTemplate.execute("ALTER TABLE audit_events ENABLE TRIGGER ALL");
    }

    @Test
    void search_byActor_isExactAndCaseInsensitive() {
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-01T10:00:00Z"), "User:42", "login", "session", "success");
        insertEvent(
                UUID.randomUUID(), Instant.parse("2026-05-01T09:00:00Z"), "user:42", "update", "project:1", "success");
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-01T08:00:00Z"), "user:420", "login", "session", "denied");

        var response = search("/audit-events?actor={actor}", "USER:42");

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(item -> item.actor()).containsExactly("User:42", "user:42");
    }

    @Test
    void search_byResource_isExactAndCaseInsensitive() {
        insertEvent(
                UUID.randomUUID(),
                Instant.parse("2026-05-01T10:00:00Z"),
                "user:1",
                "resource.updated",
                "Project:1",
                "success");
        insertEvent(
                UUID.randomUUID(),
                Instant.parse("2026-05-01T09:00:00Z"),
                "user:2",
                "resource.deleted",
                "project:1",
                "success");
        insertEvent(
                UUID.randomUUID(),
                Instant.parse("2026-05-01T08:00:00Z"),
                "user:3",
                "resource.updated",
                "project:10",
                "success");

        var response = search("/audit-events?resource={resource}", "PROJECT:1");

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(item -> item.resource()).containsExactly("Project:1", "project:1");
    }

    @Test
    void search_blankActorAndResourceFiltersAreIgnored() {
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-01T10:00:00Z"), "user:1", "login", "project:1", "success");
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-01T09:00:00Z"), "user:2", "logout", "project:2", "success");

        var response = search("/audit-events?actor={actor}&resource={resource}", "  ", " ");

        assertThat(response.items()).hasSize(2);
    }

    @Test
    void search_dateOnlyBoundsAreNormalizedInUtc() {
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-09T23:59:59Z"), "user:1", "login", "session", "success");
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-10T00:00:00Z"), "user:2", "login", "session", "success");
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-10T23:59:59.999999Z"), "user:3", "login", "session", "success");
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-11T00:00:00Z"), "user:4", "login", "session", "success");

        var response = search("/audit-events?from={from}&to={to}", "2026-05-10", "2026-05-10");

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(item -> item.actor()).containsExactly("user:3", "user:2");
    }

    @Test
    void search_defaultLimitIs50() {
        for (int index = 0; index < 55; index++) {
            insertEvent(
                    UUID.randomUUID(),
                    Instant.parse("2026-05-01T00:00:00Z").plusSeconds(index),
                    "user:" + index,
                    "login",
                    "session",
                    "success");
        }

        var response = search("/audit-events");

        assertThat(response.items()).hasSize(50);
        assertThat(response.nextCursor()).isNotBlank();
    }

    @Test
    void search_limitSupportsSmallerPagesAndStableCursorTraversal() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000012");
        UUID thirdId = UUID.fromString("00000000-0000-0000-0000-000000000013");
        insertEvent(firstId, Instant.parse("2026-05-01T08:00:00Z"), "user:1", "login", "session", "success");
        insertEvent(secondId, Instant.parse("2026-05-01T09:00:00Z"), "user:2", "login", "session", "success");
        insertEvent(thirdId, Instant.parse("2026-05-01T10:00:00Z"), "user:3", "login", "session", "success");

        var firstPage = search("/audit-events?limit=2");
        var secondPage = search("/audit-events?limit=2&cursor={cursor}", firstPage.nextCursor());

        assertThat(firstPage.items()).extracting(item -> item.id()).containsExactly(thirdId, secondId);
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(secondPage.items()).extracting(item -> item.id()).containsExactly(firstId);
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(List.of(
                        firstPage.items().get(0).id(),
                        firstPage.items().get(1).id(),
                        secondPage.items().get(0).id()))
                .containsExactly(thirdId, secondId, firstId);
    }

    @Test
    void search_sameTimestampUsesIdDescTieBreak() {
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        Instant timestamp = Instant.parse("2026-05-01T10:00:00Z");
        insertEvent(lowerId, timestamp, "user:1", "login", "session", "success");
        insertEvent(higherId, timestamp, "user:2", "login", "session", "success");

        var response = search("/audit-events");

        assertThat(response.items()).extracting(item -> item.id()).containsExactly(higherId, lowerId);
    }

    @Test
    void search_freshFirstPageCanSeeNewlyInsertedRows() {
        UUID originalId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID newerId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        insertEvent(originalId, Instant.parse("2026-05-01T10:00:00Z"), "user:1", "login", "session", "success");

        var firstPage = search("/audit-events?limit=1");
        insertEvent(newerId, Instant.parse("2026-05-01T11:00:00Z"), "user:2", "login", "session", "success");
        var freshFirstPage = search("/audit-events?limit=1");

        assertThat(firstPage.items()).extracting(item -> item.id()).containsExactly(originalId);
        assertThat(freshFirstPage.items()).extracting(item -> item.id()).containsExactly(newerId);
    }

    @Test
    void search_invalidInputReturnsStructured400Responses() throws Exception {
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-01T10:00:00Z"), "user:1", "login", "session", "success");
        insertEvent(UUID.randomUUID(), Instant.parse("2026-05-01T09:00:00Z"), "user:2", "login", "session", "success");

        assertInvalidQuery("/audit-events?from={from}", "INVALID_FROM", "not-a-timestamp");
        assertInvalidQuery("/audit-events?from={from}", "INVALID_FROM", "2026-05-01T10:00:00+02:00");
        assertInvalidQuery("/audit-events?to={to}", "INVALID_TO", "bad-to");
        assertInvalidQuery(
                "/audit-events?from={from}&to={to}",
                "INVALID_TIME_RANGE",
                "2026-05-02T10:00:00Z",
                "2026-05-01T10:00:00Z");
        assertInvalidQuery("/audit-events?limit=0", "INVALID_LIMIT");
        assertInvalidQuery("/audit-events?limit=51", "INVALID_LIMIT");
        assertInvalidQuery("/audit-events?cursor={cursor}", "INVALID_CURSOR", "not-a-cursor");

        var validFirstPage = search("/audit-events?limit=1");
        assertInvalidQuery(
                "/audit-events?actor={actor}&limit=1&cursor={cursor}",
                "INVALID_CURSOR",
                "different-user",
                validFirstPage.nextCursor());
        assertInvalidQuery("/audit-events?limit=2&cursor={cursor}", "INVALID_CURSOR", validFirstPage.nextCursor());
        assertInvalidQuery("/audit-events?cursor={cursor}", "INVALID_CURSOR", expireCursor(validFirstPage.nextCursor()));
    }

    private SearchAuditEventsResponse search(String path, Object... uriVariables) {
        return restTemplate.getForObject(path, SearchAuditEventsResponse.class, uriVariables);
    }

    private void assertInvalidQuery(String path, String expectedCode, Object... uriVariables) {
        var response = restTemplate.getForEntity(path, ApiErrorResponse.class, uriVariables);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(expectedCode);
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody().message()).isNotBlank();
    }

    private String expireCursor(String cursor) throws Exception {
        String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        ObjectNode payload = (ObjectNode) objectMapper.readTree(json);
        payload.put("issuedAt", Instant.now().minus(Duration.ofHours(2)).toString());
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(payload));
    }

    private void insertEvent(UUID id, Instant timestamp, String actor, String action, String resource, String outcome) {
        jdbcTemplate.update(
                "INSERT INTO audit_events (id, timestamp, actor, action, resource, outcome) VALUES (?, ?, ?, ?, ?, ?)",
                id,
                Timestamp.from(timestamp),
                actor,
                action,
                resource,
                outcome);
    }
}
