package com.team10.sems.booking;

import java.time.Instant;
import java.util.UUID;

public record OrganizerBookingView(UUID id, UUID attendeeId, String ticketTypeName,
        int quantity, String status, String cancellationReason, Instant createdAt) { }
