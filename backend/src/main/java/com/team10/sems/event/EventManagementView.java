package com.team10.sems.event;

import java.time.Instant;
import java.util.UUID;

public record EventManagementView(UUID id, String title, String description, String location,
        Instant startsAt, Instant endsAt, int capacity, String status, long version,
        Instant createdAt, Instant updatedAt) {
}
