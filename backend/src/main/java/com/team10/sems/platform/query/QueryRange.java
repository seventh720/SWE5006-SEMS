package com.team10.sems.platform.query;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Inclusive calendar dates interpreted in Singapore, converted to a half-open interval. */
public record QueryRange(Instant from, Instant until) {
    public static QueryRange dates(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date");
        }
        var zone = ZoneId.of("Asia/Singapore");
        return new QueryRange(from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination: size must be 1–50");
        }
    }
}
