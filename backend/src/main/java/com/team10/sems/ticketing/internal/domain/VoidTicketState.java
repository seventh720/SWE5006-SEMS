package com.team10.sems.ticketing.internal.domain;

import java.time.Instant;

final class VoidTicketState implements TicketState {
    static final VoidTicketState INSTANCE = new VoidTicketState();
    private VoidTicketState() { }

    @Override public String code() { return "VOID"; }
    @Override public void checkIn(IssuedTicket ticket, Instant now) { rejectCheckIn(); }
    @Override public void voidTicket(IssuedTicket ticket, Instant now) {
        // Idempotent: preserve the first void time and any check-in history.
    }
}
