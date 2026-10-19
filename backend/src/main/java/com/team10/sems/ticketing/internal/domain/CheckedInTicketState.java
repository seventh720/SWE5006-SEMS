package com.team10.sems.ticketing.internal.domain;

import java.time.Instant;

final class CheckedInTicketState implements TicketState {
    static final CheckedInTicketState INSTANCE = new CheckedInTicketState();
    private CheckedInTicketState() { }

    @Override public String code() { return "CHECKED_IN"; }
    @Override public void checkIn(IssuedTicket ticket, Instant now) { rejectCheckIn(); }
    // Event cancellation may void used tickets. Attendee cancellation is rejected by the caller.
    @Override public void voidTicket(IssuedTicket ticket, Instant now) { ticket.markVoided(now); }
}
