package com.team10.sems.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingView(UUID id, String eventTitle, String eventLocation,
        Instant eventStartsAt, Instant eventEndsAt, String ticketTypeName, int quantity,
        long unitPriceMinor, long totalAmountMinor, String currency, String status,
        String paymentStatus, String cancellationReason, Instant createdAt, AttendeeInfo attendeeInfo, String customFieldLabel, CurrentEvent currentEvent) {
    public record CurrentEvent(String title, String location, Instant startsAt, Instant endsAt) { }
    public BookingView withCurrentEvent(com.team10.sems.event.EventAccessView event) {
        return new BookingView(id, eventTitle, eventLocation, eventStartsAt, eventEndsAt, ticketTypeName,
                quantity, unitPriceMinor, totalAmountMinor, currency, status, paymentStatus, cancellationReason,
                createdAt, attendeeInfo, customFieldLabel,
                new CurrentEvent(event.title(), event.location(), event.startsAt(), event.endsAt()));
    }
}
