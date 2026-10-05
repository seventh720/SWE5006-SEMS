package com.team10.sems.event;

import java.time.Instant;
import java.util.UUID;

public record EventAccessView(
        UUID id,
        UUID organizerId,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        String status) {
}