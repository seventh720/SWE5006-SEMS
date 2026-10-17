package com.team10.sems.reporting.internal.application;

import com.team10.sems.event.EventAccessService;
import com.team10.sems.event.EventAccessView;
import com.team10.sems.reporting.EventStatistics;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only reporting service. Aggregates booking and issued-ticket rows directly via a
 * dedicated JDBC read model so statistics never load whole collections or lock rows.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
public class ReportingService {

    private final JdbcTemplate jdbc;
    private final EventAccessService events;

    public ReportingService(JdbcTemplate jdbc, EventAccessService events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public EventStatistics statistics(UUID owner, UUID eventId) {
        EventAccessView event = events.requireOwnedEvent(eventId, owner);
        return new EventStatistics(
                eventId,
                event.status(),
                event.capacity(),
                sumBookingQuantity(eventId, "CONFIRMED"),
                sumBookingQuantity(eventId, "CANCELLED"),
                countTickets(eventId, "CHECKED_IN"));
    }

    private long sumBookingQuantity(UUID eventId, String status) {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM bookings WHERE event_id = ? AND status = ?",
                Long.class, eventId, status);
        return sum == null ? 0L : sum;
    }

    private long countTickets(UUID eventId, String status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM issued_tickets WHERE event_id = ? AND status = ?",
                Long.class, eventId, status);
        return count == null ? 0L : count;
    }
}
