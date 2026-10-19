package com.team10.sems.notification.internal.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final java.time.Clock clock;
    public NotificationService(JdbcTemplate jdbc, java.time.Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public NotificationPage list(UUID user, int page, int size, boolean unreadOnly) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        String filter = " WHERE user_id=?" + (unreadOnly ? " AND read_at IS NULL" : "");
        long total = count("SELECT count(*) FROM notifications" + filter, user);
        long unread = count("SELECT count(*) FROM notifications WHERE user_id=? AND read_at IS NULL", user);
        var rows = jdbc.query("SELECT * FROM notifications" + filter + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                this::map, user, size, (long) page * size);
        return new NotificationPage(rows, (total + size - 1) / size, unread);
    }

    @Transactional
    public void markRead(UUID user, UUID id) {
        int changed = jdbc.update("UPDATE notifications SET read_at=COALESCE(read_at,?) WHERE id=? AND user_id=?", java.sql.Timestamp.from(clock.instant()), id, user);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
    }

    private long count(String sql, UUID user) {
        return java.util.Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, user));
    }

    private NotificationView map(ResultSet row, int index) throws SQLException {
        var read = row.getTimestamp("read_at");
        return new NotificationView(row.getObject("id", UUID.class), row.getString("type"), row.getString("title"),
                row.getString("message"), row.getTimestamp("created_at").toInstant(), read == null ? null : read.toInstant(),
                row.getObject("event_id", UUID.class), row.getObject("booking_id", UUID.class), row.getObject("ticket_type_id", UUID.class));
    }

    public record NotificationView(UUID id, String type, String title, String message, Instant createdAt,
            Instant readAt, UUID eventId, UUID bookingId, UUID ticketTypeId) { }
    public record NotificationPage(List<NotificationView> items, long totalPages, long unreadCount) { }
}
