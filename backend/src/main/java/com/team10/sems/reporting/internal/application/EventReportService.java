package com.team10.sems.reporting.internal.application;

import com.team10.sems.platform.query.QueryRange;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class EventReportService {
    private final NamedParameterJdbcTemplate db;
    public EventReportService(JdbcTemplate jdbc) { db = new NamedParameterJdbcTemplate(jdbc); }

    public record Row(UUID eventId, String eventTitle, String eventStatus, Instant startsAt, Instant endsAt,
            UUID ticketTypeId, String ticketType, long quota, long available,
            long confirmed, long cancelled, long checkedIn) { }
    public record Totals(long confirmed, long cancelled, long checkedIn) { }
    public record ReportPage(List<Row> items, int page, int size, long totalElements, long totalPages, Totals totals) { }

    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ReportPage owned(UUID owner, UUID event, String keyword, String ticketType, String bookingStatus,
            LocalDate from, LocalDate to, int page, int size) {
        return query(owner, event, keyword, ticketType, bookingStatus, from, to, page, size);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ReportPage all(UUID event, String keyword, String ticketType, String bookingStatus,
            LocalDate from, LocalDate to, int page, int size) {
        return query(null, event, keyword, ticketType, bookingStatus, from, to, page, size);
    }

    private ReportPage query(UUID owner, UUID event, String keyword, String ticketType, String bookingStatus,
            LocalDate from, LocalDate to, int page, int size) {
        QueryRange.page(page, size);
        var dates = QueryRange.dates(from, to);
        if (!java.util.Set.of("ALL", "CONFIRMED", "CANCELLED").contains(bookingStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown booking status filter");
        }
        var params = new MapSqlParameterSource().addValue("keyword", keyword.strip())
                .addValue("ticketType", ticketType.strip()).addValue("bookingStatus", bookingStatus)
                .addValue("limit", size).addValue("offset", (long) page * size);
        String sql = """
                SELECT e.id event_id, e.title, e.status, e.starts_at, e.ends_at,
                       tt.id ticket_type_id, tt.name, tt.quota, tt.quota-tt.booked_quantity available,
                       COALESCE(b.confirmed,0) confirmed, COALESCE(b.cancelled,0) cancelled,
                       COALESCE(c.checked_in,0) checked_in
                FROM ticket_types tt JOIN events e ON e.id=tt.event_id
                LEFT JOIN (
                    SELECT ticket_type_id,
                           SUM(CASE WHEN status='CONFIRMED' THEN quantity ELSE 0 END) confirmed,
                           SUM(CASE WHEN status='CANCELLED' THEN quantity ELSE 0 END) cancelled
                    FROM bookings WHERE (:bookingStatus='ALL' OR status=:bookingStatus)
                    GROUP BY ticket_type_id
                ) b ON b.ticket_type_id=tt.id
                LEFT JOIN (
                    SELECT b.ticket_type_id, COUNT(*) checked_in
                    FROM issued_tickets t JOIN bookings b ON b.id=t.booking_id
                    WHERE t.status='CHECKED_IN' AND b.status='CONFIRMED'
                      AND (:bookingStatus='ALL' OR b.status=:bookingStatus)
                    GROUP BY b.ticket_type_id
                ) c ON c.ticket_type_id=tt.id
                WHERE strpos(lower(e.title),lower(:keyword))>0 AND strpos(lower(tt.name),lower(:ticketType))>0
                """;
        if (owner != null) { sql += " AND e.organizer_id=:owner"; params.addValue("owner", owner); }
        if (event != null) { sql += " AND e.id=:event"; params.addValue("event", event); }
        if (dates.from() != null) { sql += " AND e.starts_at>=:from"; params.addValue("from", java.sql.Timestamp.from(dates.from())); }
        if (dates.until() != null) { sql += " AND e.starts_at<:until"; params.addValue("until", java.sql.Timestamp.from(dates.until())); }
        // Aggregate each source before joining; ticket counts must not multiply booking quantities.
        var summary = db.queryForMap("SELECT count(*) total, COALESCE(sum(confirmed),0) confirmed, "
                + "COALESCE(sum(cancelled),0) cancelled, COALESCE(sum(checked_in),0) checked_in FROM (" + sql + ") report", params);
        long total = ((Number) summary.get("total")).longValue();
        var totals = new Totals(((Number) summary.get("confirmed")).longValue(),
                ((Number) summary.get("cancelled")).longValue(), ((Number) summary.get("checked_in")).longValue());
        var rows = db.query(sql + " ORDER BY e.starts_at DESC,e.id,tt.name,tt.id LIMIT :limit OFFSET :offset", params,
                (rs, n) -> new Row(rs.getObject("event_id", UUID.class), rs.getString("title"), rs.getString("status"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                        rs.getObject("ticket_type_id", UUID.class), rs.getString("name"), rs.getLong("quota"),
                        rs.getLong("available"), rs.getLong("confirmed"), rs.getLong("cancelled"), rs.getLong("checked_in")));
        return new ReportPage(rows, page, size, total, (total + size - 1) / size, totals);
    }
}
