package com.team10.sems.ticketing.internal.domain;

import java.time.Instant;

final class ValidTicketState implements TicketState {
    static final ValidTicketState INSTANCE = new ValidTicketState();
    private ValidTicketState() { }

    @Override public String code() { return "VALID"; }
    @Override public void checkIn(IssuedTicket ticket, Instant now) { ticket.markCheckedIn(now); }
    @Override public void voidTicket(IssuedTicket ticket, Instant now) { ticket.markVoided(now); }
}
