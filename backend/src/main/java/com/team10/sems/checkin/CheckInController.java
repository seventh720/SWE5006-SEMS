package com.team10.sems.checkin;

import com.team10.sems.event.EventAccessService;
import com.team10.sems.notification.NotificationDeliveryService;
import com.team10.sems.ticketing.TicketLifecycleService;
import com.team10.sems.ticketing.TicketView;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/staff")
public class CheckInController {
    private final NotificationDeliveryService notifications;
    private final Clock clock;
    private final JdbcTemplate db;
    private final EventAccessService events;
    private final TicketLifecycleService lifecycle;

    public CheckInController(JdbcTemplate db, EventAccessService events, TicketLifecycleService lifecycle, Clock clock, NotificationDeliveryService notifications) {
        this.notifications = notifications;
        this.clock = clock;
        this.db = db;
        this.events = events;
        this.lifecycle = lifecycle;
    }

    public record AssignedEvent(UUID id, String title, Instant startsAt, Instant endsAt) { }
    public record Verification(UUID ticketId, UUID bookingId, UUID eventId, String status, Instant checkedInAt) { }
    public record CheckInRecord(UUID ticketId, UUID bookingId, UUID operatorId, Instant checkedInAt) { }
    public record TicketCode(String code) { }

    @GetMapping("/events")
    @PreAuthorize("hasRole('STAFF')")
    public List<AssignedEvent> assigned(@AuthenticationPrincipal Jwt jwt) {
        return db.query("""
                SELECT e.id, e.title, e.starts_at, e.ends_at FROM events e
                JOIN event_staff_assignments a ON a.event_id = e.id
                WHERE a.staff_id = ? ORDER BY e.starts_at DESC
                """, (rs, n) -> new AssignedEvent(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant()), actor(jwt));
    }

    public record StaffMember(UUID id, String username, boolean eligible, boolean assigned) { }
    public record Assignments(String eventTitle, List<StaffMember> staff) { }

    @GetMapping("/events/{eventId}/assignments")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Transactional(readOnly = true)
    public Assignments assignments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        var event = events.requireOwnedEvent(eventId, actor(jwt));
        List<StaffMember> staff = db.query("""
                SELECT u.id, u.username,
                  (u.status='ACTIVE' AND EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role_code='STAFF')) AS eligible,
                  (a.staff_id IS NOT NULL) AS assigned
                FROM users u LEFT JOIN event_staff_assignments a ON a.staff_id=u.id AND a.event_id=?
                WHERE a.staff_id IS NOT NULL OR (u.status='ACTIVE' AND EXISTS
                  (SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role_code='STAFF'))
                ORDER BY u.username, u.id
                """, (rs, n) -> new StaffMember(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getBoolean("eligible"), rs.getBoolean("assigned")), eventId);
        return new Assignments(event.title(), staff);
    }

    // Organizer-controlled assignment. Role alone never grants event access.
    @PutMapping("/events/{eventId}/assignments/{staffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Transactional
    public void assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID staffId) {
        var event = events.lockOwnedEvent(eventId, actor(jwt));
        if (db.queryForObject("SELECT COUNT(*) FROM user_roles r JOIN users u ON u.id=r.user_id WHERE r.user_id = ? AND r.role_code = 'STAFF' AND u.status='ACTIVE'", Integer.class, staffId) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not STAFF");
        }
        int changed = db.update("INSERT INTO event_staff_assignments(event_id,staff_id) VALUES (?,?) ON CONFLICT DO NOTHING", eventId, staffId);
        if (changed > 0) notifications.staffAssignment(staffId, eventId, event.title(), UUID.randomUUID().toString(), true);
    }

    @DeleteMapping("/events/{eventId}/assignments/{staffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Transactional
    public void unassign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @PathVariable UUID staffId) {
        var event = events.lockOwnedEvent(eventId, actor(jwt));
        int changed = db.update("DELETE FROM event_staff_assignments WHERE event_id=? AND staff_id=?", eventId, staffId);
        if (changed > 0) notifications.staffAssignment(staffId, eventId, event.title(), UUID.randomUUID().toString(), false);
    }

    @PostMapping("/events/{eventId}/verify")
    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public Verification verify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @RequestBody TicketCode code) {
        var event = events.lockEvent(eventId);
        requireAssigned(eventId, actor(jwt));
        requireCheckInWindow(event.startsAt(), event.endsAt(), event.status());
        return lookup(eventId, parse(code));
    }

    @PostMapping("/events/{eventId}/check-ins")
    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public TicketView checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @RequestBody TicketCode code) {
        // Lock event first, then verify assignment and ticket, matching cancellation lock order.
        var event = events.lockEvent(eventId);
        UUID operator = actor(jwt);
        requireAssigned(eventId, operator);
        requireCheckInWindow(event.startsAt(), event.endsAt(), event.status());
        Verification ticket = lookup(eventId, parse(code));
        TicketView result = lifecycle.checkIn(eventId, ticket.bookingId(), ticket.ticketId());
        db.update("INSERT INTO ticket_check_in_records(id,ticket_id,event_id,booking_id,operator_id,checked_in_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), result.id(), eventId, result.bookingId(), operator, java.sql.Timestamp.from(result.checkedInAt()));
        return result;
    }

    @GetMapping("/events/{eventId}/check-ins")
    @PreAuthorize("hasRole('STAFF')")
    @Transactional
    public List<CheckInRecord> records(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        events.lockEvent(eventId);
        requireAssigned(eventId, actor(jwt));
        return db.query("""
                SELECT ticket_id, booking_id, operator_id, checked_in_at FROM ticket_check_in_records
                WHERE event_id=? ORDER BY checked_in_at DESC LIMIT 200
                """, (rs, n) -> new CheckInRecord(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getTimestamp(4).toInstant()), eventId);
    }

    private Verification lookup(UUID eventId, UUID ticketId) {
        List<Verification> found = db.query("""
                SELECT id, booking_id, event_id, status, checked_in_at FROM issued_tickets
                WHERE id=? AND event_id=?
                """, (rs, n) -> new Verification(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4),
                rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()), ticketId, eventId);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found for this event");
        return found.get(0);
    }

    private void requireAssigned(UUID eventId, UUID staffId) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM event_staff_assignments WHERE event_id=? AND staff_id=?",
                Integer.class, eventId, staffId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not assigned to this event");
    }

    private void requireCheckInWindow(Instant start, Instant end, String status) {
        Instant now = clock.instant();
        if (!"PUBLISHED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Check-in is unavailable because this event is not published.");
        }
        if (now.isBefore(start.minusSeconds(900)) || !now.isBefore(start.plusSeconds(900)) || !now.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Check-in opens 15 minutes before the event and closes 15 minutes after it starts (or when it ends, if earlier)");
        }
    }

    private static UUID parse(TicketCode input) {
        if (input == null || input.code() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket code required");
        String raw = input.code().trim();
        // Accept raw UUID or an explicit sems:ticket:<uuid> QR payload; reject arbitrary URLs.
        if (raw.startsWith("sems:ticket:")) raw = raw.substring("sems:ticket:".length());
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticket code"); }
    }

    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
