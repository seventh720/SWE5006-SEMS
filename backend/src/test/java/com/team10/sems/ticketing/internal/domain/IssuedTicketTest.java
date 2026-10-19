package com.team10.sems.ticketing.internal.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class IssuedTicketTest {
    private final Instant checkedAt = Instant.parse("2026-10-27T02:30:00Z");
    private final Instant voidedAt = checkedAt.plusSeconds(60);

    private IssuedTicket ticket() { return IssuedTicket.issue(UUID.randomUUID(), UUID.randomUUID(), 1); }

    @Test
    void newTicketCanCheckInAndRecordsItsFirstUse() {
        var ticket = ticket();
        assertEquals("VALID", ticket.toView().status());
        assertFalse(ticket.hasBeenUsed());
        ticket.checkIn(checkedAt);
        assertEquals("CHECKED_IN", ticket.toView().status());
        assertEquals(checkedAt, ticket.toView().checkedInAt());
        assertNull(ticket.toView().voidedAt());
        assertTrue(ticket.hasBeenUsed());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void voidingPreservesWhetherTheTicketWasUsed(boolean used) {
        var ticket = ticket();
        if (used) ticket.checkIn(checkedAt);
        ticket.voidTicket(voidedAt);
        assertEquals("VOID", ticket.toView().status());
        assertEquals(voidedAt, ticket.toView().voidedAt());
        assertEquals(used ? checkedAt : null, ticket.toView().checkedInAt());
        assertEquals(used, ticket.hasBeenUsed());
        var firstVoid = ticket.toView();
        ticket.voidTicket(voidedAt.plusSeconds(60));
        assertEquals(firstVoid, ticket.toView());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHECKED_IN", "VOID", "USED_THEN_VOID"})
    void usedOrVoidedTicketRejectsCheckInWithoutMutatingHistory(String state) {
        var ticket = ticket();
        if (!state.equals("VOID")) ticket.checkIn(checkedAt);
        if (!state.equals("CHECKED_IN")) ticket.voidTicket(voidedAt);
        var before = ticket.toView();
        var error = assertThrows(ResponseStatusException.class, () -> ticket.checkIn(voidedAt.plusSeconds(1)));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(before, ticket.toView());
    }
}
