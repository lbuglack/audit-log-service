package com.auditlog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.response.AuditEventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class DbImmutabilityIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void update_viaJdbc_isRejectedByTrigger() {
        var event = createEvent();

        assertThatThrownBy(() ->
                        jdbcTemplate.update("UPDATE audit_events SET actor = 'tampered' WHERE id = ?", event.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void delete_viaJdbc_isRejectedByTrigger() {
        var event = createEvent();

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_events WHERE id = ?", event.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void truncate_viaJdbc_isRejectedByTrigger() {
        createEvent();

        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE audit_events"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private AuditEventResponse createEvent() {
        return restTemplate.postForObject(
                "/audit-events",
                new CreateAuditEventRequest("user:1", "login", "session", "success", null),
                AuditEventResponse.class);
    }
}
