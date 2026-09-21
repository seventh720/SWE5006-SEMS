package com.team10.sems.event;

import java.time.Instant;
import java.util.UUID;

/** Public event information; organizer and internal version are not exposed. */
public record EventView(UUID id, String title, String description, String location,
        Instant startsAt, Instant endsAt, int capacity, String status) {
}
