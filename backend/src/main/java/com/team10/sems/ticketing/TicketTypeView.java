package com.team10.sems.ticketing;

import java.util.UUID;

public record TicketTypeView(
        UUID id,
        UUID eventId,
        String name,
        long priceMinor,
        String currency,
        int quota,
        int bookedQuantity,
        Long version) {
}