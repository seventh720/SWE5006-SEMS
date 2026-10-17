package com.team10.sems.reporting;

import java.util.UUID;

/** Read-only aggregate of reserved, cancelled and checked-in ticket counts for one event. */
public record EventStatistics(
        UUID eventId,
        String eventStatus,
        int capacity,
        long validReservedTickets,
        long cancelledTickets,
        long checkedInTickets) {
}
