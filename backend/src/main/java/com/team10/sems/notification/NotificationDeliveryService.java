package com.team10.sems.notification;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal API: callers authorize recipients and supply a stable business-operation key. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationDeliveryService {
    private final JdbcTemplate jdbc;
    private final java.time.Clock clock;

    public NotificationDeliveryService(JdbcTemplate jdbc, java.time.Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public void send(UUID userId, NotificationType type, String sourceKey, String title, String message,
            UUID eventId, UUID bookingId, UUID ticketTypeId) {
        jdbc.update("""
                INSERT INTO notifications(id,user_id,type,source_key,title,message,event_id,booking_id,ticket_type_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (user_id,type,source_key) DO NOTHING
                """, UUID.randomUUID(), userId, type.name(), sourceKey, title, message, eventId, bookingId, ticketTypeId, java.sql.Timestamp.from(clock.instant()));
    }

    /** Call in the assignment transaction after validating organizer ownership and locking the event. */
    public void staffAssignment(UUID staffId, UUID eventId, String eventTitle, String operationKey, boolean assigned) {
        send(staffId, assigned ? NotificationType.STAFF_ASSIGNED : NotificationType.STAFF_REVOKED,
                operationKey, assigned ? "New staff assignment" : "Staff assignment removed",
                eventTitle + (assigned ? ": you have been assigned to this event." : ": your event access has been removed."),
                eventId, null, null);
    }
}
