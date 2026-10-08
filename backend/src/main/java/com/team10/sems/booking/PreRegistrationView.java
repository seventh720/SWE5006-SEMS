package com.team10.sems.booking;

import java.time.Instant;
import java.util.UUID;

public record PreRegistrationView(UUID id, UUID eventId, String eventTitle, UUID ticketTypeId,
        String ticketTypeName, int quantity, AttendeeInfo attendeeInfo, String customFieldLabel,
        Instant registrationOpensAt, Instant registrationClosesAt, String status,
        UUID bookingId, Instant updatedAt) { }
