package com.team10.sems.ticketing;

import java.time.Instant;
import java.util.UUID;

public record TicketView(UUID id, UUID bookingId, UUID eventId, int ordinal,
        String status, Instant checkedInAt, Instant voidedAt) { }
