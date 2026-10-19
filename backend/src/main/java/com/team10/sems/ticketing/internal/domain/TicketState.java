package com.team10.sems.ticketing.internal.domain;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** State-specific ticket behaviour; state objects are stateless and never persisted. */
sealed interface TicketState permits ValidTicketState, CheckedInTicketState, VoidTicketState {
    String code();
    void checkIn(IssuedTicket ticket, Instant now);
    void voidTicket(IssuedTicket ticket, Instant now);

    static TicketState fromCode(String code) {
        // Persistence mapping only. Transition decisions live in the concrete states.
        return switch (code) {
            case "VALID" -> ValidTicketState.INSTANCE;
            case "CHECKED_IN" -> CheckedInTicketState.INSTANCE;
            case "VOID" -> VoidTicketState.INSTANCE;
            default -> throw new IllegalStateException("Unknown ticket state: " + code);
        };
    }

    default void rejectCheckIn() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket has already been used or voided");
    }
}
