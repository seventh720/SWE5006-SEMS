package com.team10.sems.ticketing.internal.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTypeTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    // =========================================================
    // Creation
    // =========================================================

    @Test
    void createsValidTicketType() {
        TicketType ticketType = TicketType.create(
                EVENT_ID,
                "General Admission",
                2500,
                "SGD",
                100);

        assertEquals(EVENT_ID, ticketType.getEventId());
        assertEquals("General Admission", ticketType.getName());
        assertEquals(2500, ticketType.getPriceMinor());
        assertEquals("SGD", ticketType.getCurrency());
        assertEquals(100, ticketType.getQuota());
        assertEquals(0, ticketType.getBookedQuantity());
    }

    @Test
    void allowsFreeTicketType() {
        TicketType ticketType = TicketType.create(
                EVENT_ID,
                "Free Admission",
                0,
                "SGD",
                50);

        assertEquals(0, ticketType.getPriceMinor());
    }

    // =========================================================
    // Price / quota / currency / name validation
    // =========================================================

    @Test
    void rejectsNegativePrice() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TicketType.create(
                        EVENT_ID,
                        "Invalid",
                        -1,
                        "SGD",
                        100));
    }

    @Test
    void rejectsZeroQuota() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TicketType.create(
                        EVENT_ID,
                        "Invalid",
                        1000,
                        "SGD",
                        0));
    }

    @Test
    void rejectsNegativeQuota() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TicketType.create(
                        EVENT_ID,
                        "Invalid",
                        1000,
                        "SGD",
                        -1));
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TicketType.create(
                        EVENT_ID,
                        "Invalid",
                        1000,
                        "USD",
                        100));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TicketType.create(
                        EVENT_ID,
                        "   ",
                        1000,
                        "SGD",
                        100));
    }

    // =========================================================
    // Editing / optimistic version validation
    // =========================================================

    @Test
    void editsTicketTypeWithCurrentVersion() {
        TicketType ticketType = TicketType.create(
                EVENT_ID,
                "Early Bird",
                1000,
                "SGD",
                100);

        ticketType.edit(
                "VIP",
                5000,
                "SGD",
                50,
                ticketType.getVersion());

        assertEquals("VIP", ticketType.getName());
        assertEquals(5000, ticketType.getPriceMinor());
        assertEquals("SGD", ticketType.getCurrency());
        assertEquals(50, ticketType.getQuota());
    }

    @Test
    void rejectsStaleVersion() {
        TicketType ticketType = TicketType.create(
                EVENT_ID,
                "General",
                1000,
                "SGD",
                100);

        assertThrows(
                IllegalStateException.class,
                () -> ticketType.edit(
                        "VIP",
                        5000,
                        "SGD",
                        50,
                        ticketType.getVersion() + 1));
    }

    // =========================================================
    // Reservation / bookability validation
    // =========================================================

    @Test
    void reservesAvailableFreeTickets() {
        TicketType ticketType = TicketType.create(
                EVENT_ID,
                "Free Admission",
                0,
                "SGD",
                10);

        ticketType.reserve(3);

        assertEquals(3, ticketType.getBookedQuantity());
    }

    @Test
    void rejectsReservationBeyondQuota() {
        TicketType ticketType = TicketType.create(
                EVENT_ID,
                "Free Admission",
                0,
                "SGD",
                2);

        assertThrows(
                IllegalStateException.class,
                () -> ticketType.reserve(3));

        assertEquals(0, ticketType.getBookedQuantity());
    }

    @Test
    void rejectsZeroReservationQuantity() {
        TicketType ticketType = TicketType.create(
                EVENT_ID,
                "Free Admission",
                0,
                "SGD",
                10);

        assertThrows(
                IllegalArgumentException.class,
                () -> ticketType.reserve(0));

        assertEquals(0, ticketType.getBookedQuantity());
    }

    @Test
    void rejectsMoreThanTenTicketsPerReservation() {
        TicketType ticketType = TicketType.create(
                EVENT_ID,
                "Free Admission",
                0,
                "SGD",
                20);

        assertThrows(
                IllegalArgumentException.class,
                () -> ticketType.reserve(11));

        assertEquals(0, ticketType.getBookedQuantity());
    }

    @Test
    void rejectsPaidTicketReservation() {
        TicketType ticketType = TicketType.create(
                EVENT_ID,
                "VIP",
                5000,
                "SGD",
                100);

        assertThrows(
                IllegalStateException.class,
                () -> ticketType.reserve(1));

        assertEquals(0, ticketType.getBookedQuantity());
    }
}