package com.team10.sems.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingView(UUID id, String eventTitle, String eventLocation,
        Instant eventStartsAt, Instant eventEndsAt, String ticketTypeName, int quantity,
        long unitPriceMinor, long totalAmountMinor, String currency, String status,
        String paymentStatus, String cancellationReason, Instant createdAt, AttendeeInfo attendeeInfo, String customFieldLabel) { }
