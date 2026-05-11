package com.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.response.AuditEventResponse;
import com.auditlog.dto.response.SearchAuditEventsResponse;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditEventControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("ALTER TABLE audit_events DISABLE TRIGGER ALL");
        jdbcTemplate.execute("TRUNCATE audit_events");
        jdbcTemplate.execute("ALTER TABLE audit_events ENABLE TRIGGER ALL");
    }

    @Test
    void create_allFields_returns201WithPersistedEvent() {
        var request = new CreateAuditEventRequest(
                "user:42", "resource.updated", "project:1", "success", Map.of("ip", "127.0.0.1", "changes", "title"));

        var response = restTemplate.postForEntity("/audit-events", request, AuditEventResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.actor()).isEqualTo("user:42");
        assertThat(body.action()).isEqualTo("resource.updated");
        assertThat(body.resource()).isEqualTo("project:1");
        assertThat(body.outcome()).isEqualTo("success");
        assertThat(body.context()).containsEntry("ip", "127.0.0.1");
    }

    @Test
    void create_withoutContext_returns201() {
        var request = new CreateAuditEventRequest("svc:auth", "user.login", "session", "denied", null);

        var response = restTemplate.postForEntity("/audit-events", request, AuditEventResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().context()).isNull();
    }

    @Test
    void create_timestampIsAssignedByServer() {
        var before = Instant.now().minusSeconds(1);
        var request = new CreateAuditEventRequest("user:1", "data.export", "report:7", "success", null);

        var body = restTemplate.postForObject("/audit-events", request, AuditEventResponse.class);

        assertThat(body).isNotNull();
        assertThat(body.timestamp()).isBetween(before, Instant.now().plusSeconds(1));
    }

    @Test
    void create_allOutcomeValues_areAccepted() {
        for (String outcome : new String[] {"success", "denied", "error"}) {
            var request = new CreateAuditEventRequest("user:1", "login", "session", outcome, null);
            var response = restTemplate.postForEntity("/audit-events", request, AuditEventResponse.class);
            assertThat(response.getStatusCode())
                    .as("outcome '%s' should be accepted", outcome)
                    .isEqualTo(HttpStatus.CREATED);
        }
    }

    @Test
    void create_missingActor_returns400() {
        var response = restTemplate.postForEntity(
                "/audit-events", Map.of("action", "login", "resource", "session", "outcome", "success"), Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_blankActor_returns400() {
        var request = new CreateAuditEventRequest("  ", "login", "session", "success", null);
        var response = restTemplate.postForEntity("/audit-events", request, Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_missingAction_returns400() {
        var response = restTemplate.postForEntity(
                "/audit-events", Map.of("actor", "user:1", "resource", "session", "outcome", "success"), Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_missingResource_returns400() {
        var response = restTemplate.postForEntity(
                "/audit-events", Map.of("actor", "user:1", "action", "login", "outcome", "success"), Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_missingOutcome_returns400() {
        var response = restTemplate.postForEntity(
                "/audit-events", Map.of("actor", "user:1", "action", "login", "resource", "session"), Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_invalidOutcome_returns400() {
        var request = new CreateAuditEventRequest("user:1", "login", "session", "unknown", null);
        var response = restTemplate.postForEntity("/audit-events", request, Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_noFilters_returnsAllEvents() {
        createEvent("user:1", "login", "session", "success");
        createEvent("user:2", "logout", "session", "success");
        createEvent("svc:batch", "data.export", "report:5", "error");

        var response = restTemplate.getForObject("/audit-events", SearchAuditEventsResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(3);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void search_byActor_returnsOnlyMatchingEvents() {
        createEvent("user:42", "login", "session", "success");
        createEvent("user:42", "resource.updated", "project:1", "success");
        createEvent("user:99", "login", "session", "denied");

        var response = restTemplate.getForObject("/audit-events?actor={actor}", SearchAuditEventsResponse.class, "user:42");

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(2).allSatisfy(event -> assertThat(event.actor()).isEqualTo("user:42"));
    }

    @Test
    void search_byResource_returnsOnlyMatchingEvents() {
        createEvent("user:1", "resource.updated", "project:1", "success");
        createEvent("user:2", "resource.deleted", "project:1", "success");
        createEvent("user:3", "resource.updated", "project:99", "success");

        var response =
                restTemplate.getForObject("/audit-events?resource={resource}", SearchAuditEventsResponse.class, "project:1");

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(2).allSatisfy(event -> assertThat(event.resource()).isEqualTo("project:1"));
    }

    @Test
    void search_byTimeRange_returnsOnlyEventsInRange() {
        var from = Instant.now().minusSeconds(2);
        createEvent("user:1", "login", "session", "success");
        var to = Instant.now().plusSeconds(2);

        var inRange = restTemplate.getForObject(
                "/audit-events?from={from}&to={to}", SearchAuditEventsResponse.class, from.toString(), to.toString());
        assertThat(inRange).isNotNull();
        assertThat(inRange.items()).hasSize(1);

        var outOfRange = restTemplate.getForObject(
                "/audit-events?from={from}&to={to}",
                SearchAuditEventsResponse.class,
                Instant.now().plusSeconds(100).toString(),
                Instant.now().plusSeconds(200).toString());
        assertThat(outOfRange).isNotNull();
        assertThat(outOfRange.items()).isEmpty();
    }

    @Test
    void search_byCombinedActorAndResource_returnsIntersection() {
        createEvent("user:1", "login", "project:1", "success");
        createEvent("user:1", "login", "project:2", "success");
        createEvent("user:2", "login", "project:1", "success");

        var response = restTemplate.getForObject(
                "/audit-events?actor={actor}&resource={resource}",
                SearchAuditEventsResponse.class,
                "user:1",
                "project:1");

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).actor()).isEqualTo("user:1");
        assertThat(response.items().get(0).resource()).isEqualTo("project:1");
    }

    @Test
    void search_noMatch_returnsEmptyList() {
        createEvent("user:1", "login", "session", "success");

        var response =
                restTemplate.getForObject("/audit-events?actor={actor}", SearchAuditEventsResponse.class, "nonexistent:actor");

        assertThat(response).isNotNull();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void search_emptyDatabase_returnsEmptyList() {
        var response = restTemplate.getForObject("/audit-events", SearchAuditEventsResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void search_resultsOrderedByTimestampDescending() {
        createEvent("user:1", "action.first", "resource", "success");
        createEvent("user:1", "action.second", "resource", "success");
        createEvent("user:1", "action.third", "resource", "success");

        var response = restTemplate.getForObject("/audit-events", SearchAuditEventsResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(3);
        assertThat(response.items().get(0).timestamp()).isAfterOrEqualTo(response.items().get(1).timestamp());
        assertThat(response.items().get(1).timestamp()).isAfterOrEqualTo(response.items().get(2).timestamp());
    }

    @Test
    void delete_auditEvent_returns404() {
        var created = createEvent("user:1", "login", "session", "success");

        var response = restTemplate.exchange("/audit-events/{id}", HttpMethod.DELETE, null, Void.class, created.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void put_auditEvent_returns404() {
        var created = createEvent("user:1", "login", "session", "success");

        var response = restTemplate.exchange("/audit-events/{id}", HttpMethod.PUT, null, Void.class, created.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private AuditEventResponse createEvent(String actor, String action, String resource, String outcome) {
        return restTemplate.postForObject(
                "/audit-events",
                new CreateAuditEventRequest(actor, action, resource, outcome, null),
                AuditEventResponse.class);
    }
}
