package com.team10.sems.booking.internal.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingRequestLock {
    private final JdbcTemplate jdbc;

    public BookingRequestLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(UUID user, String key) {
        // Serialize retries even when the same key is submitted for different events.
        // PostgreSQL releases the advisory lock on commit/rollback; the unique constraint is the backstop.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> { }, user + ":" + key);
    }
}
