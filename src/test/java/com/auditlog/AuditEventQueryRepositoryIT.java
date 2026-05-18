package com.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.auditlog.dao.entity.AuditEventEntity;
import com.auditlog.dao.repository.AuditEventQueryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditEventQueryRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AuditEventQueryRepository auditEventQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("ALTER TABLE audit_events DISABLE TRIGGER ALL");
        jdbcTemplate.execute("TRUNCATE audit_events");
        jdbcTemplate.execute("ALTER TABLE audit_events ENABLE TRIGGER ALL");
    }

    @Test
    void search_multiActorFilters_supportMixedFilterSetAndDeterministicOrder() {
        UUID firstMatchId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID secondMatchId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID sameTimestampLowerId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        UUID sameTimestampHigherId = UUID.fromString("00000000-0000-0000-0000-000000000104");

        insertEvent(firstMatchId, Instant.parse("2026-05-01T09:00:00Z"), "User:1", "login", "Project:1", "success");
        insertEvent(secondMatchId, Instant.parse("2026-05-01T10:00:00Z"), "user:2", "login", "project:1", "success");
        insertEvent(
                sameTimestampLowerId, Instant.parse("2026-05-01T11:00:00Z"), "user:1", "login", "project:1", "success");
        insertEvent(
                sameTimestampHigherId,
                Instant.parse("2026-05-01T11:00:00Z"),
                "user:2",
                "login",
                "project:1",
                "success");
        insertEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000105"),
                Instant.parse("2026-05-01T12:00:00Z"),
                "user:3",
                "login",
                "project:1",
                "success");
        insertEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000106"),
                Instant.parse("2026-05-01T10:30:00Z"),
                "user:1",
                "login",
                "project:2",
                "success");
        insertEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000107"),
                Instant.parse("2026-04-30T23:59:59Z"),
                "user:2",
                "login",
                "project:1",
                "success");

        List<AuditEventEntity> results = auditEventQueryRepository.search(
                List.of("user:1", "user:2"),
                "project:1",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T11:00:00Z"),
                null,
                null,
                10);

        assertThat(results)
                .extracting(AuditEventEntity::getId)
                .containsExactly(sameTimestampHigherId, sameTimestampLowerId, secondMatchId, firstMatchId);
    }

    @Test
    void search_multiActorFilters_supportSeekPaginationAcrossSharedTimestamps() {
        UUID oldestId = UUID.fromString("00000000-0000-0000-0000-000000000111");
        UUID middleLowerId = UUID.fromString("00000000-0000-0000-0000-000000000112");
        UUID middleHigherId = UUID.fromString("00000000-0000-0000-0000-000000000113");
        UUID newestId = UUID.fromString("00000000-0000-0000-0000-000000000114");

        insertEvent(oldestId, Instant.parse("2026-05-01T08:00:00Z"), "user:1", "login", "project:1", "success");
        insertEvent(middleLowerId, Instant.parse("2026-05-01T09:00:00Z"), "user:2", "login", "project:1", "success");
        insertEvent(middleHigherId, Instant.parse("2026-05-01T09:00:00Z"), "user:1", "login", "project:1", "success");
        insertEvent(newestId, Instant.parse("2026-05-01T10:00:00Z"), "user:2", "login", "project:1", "success");

        List<AuditEventEntity> firstPage =
                auditEventQueryRepository.search(List.of("user:1", "user:2"), "project:1", null, null, null, null, 2);

        List<AuditEventEntity> secondPage = auditEventQueryRepository.search(
                List.of("user:1", "user:2"),
                "project:1",
                null,
                null,
                firstPage.get(1).getTimestamp(),
                firstPage.get(1).getId(),
                2);

        assertThat(firstPage).extracting(AuditEventEntity::getId).containsExactly(newestId, middleHigherId);
        assertThat(secondPage).extracting(AuditEventEntity::getId).containsExactly(middleLowerId, oldestId);
    }

    @Test
    void schema_exposesActorOrientedIndexesForMultiActorQueries() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'audit_events'");

        assertThat(indexes)
                .extracting(row -> (String) row.get("indexname"))
                .contains(
                        "idx_audit_events_lower_actor_timestamp_id_desc",
                        "idx_audit_events_lower_actor_lower_resource_timestamp_id_desc");
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
