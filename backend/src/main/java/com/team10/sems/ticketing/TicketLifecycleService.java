package com.team10.sems.ticketing;

import com.team10.sems.booking.BookingAccessService;
import com.team10.sems.ticketing.internal.domain.IssuedTicket;
import com.team10.sems.ticketing.internal.persistence.IssuedTicketRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Internal transaction API, not an HTTP endpoint. The caller must enforce booking
 * ownership (issue) or STAFF/event assignment and check-in time rules (checkIn).
 * All mutations acquire the event lock before reading order or ticket state.
 */
@Service
@Transactional
public class TicketLifecycleService {
    private final java.time.Clock clock;
    private final BookingAccessService bookings;
    private final IssuedTicketRepository tickets;

    public TicketLifecycleService(BookingAccessService bookings, IssuedTicketRepository tickets, java.time.Clock clock) {
        this.clock = clock;
        this.bookings = bookings;
        this.tickets = tickets;
    }

    public List<TicketView> issue(UUID eventId, UUID bookingId) {
        var booking = bookings.lockActive(eventId, bookingId);
        var existing = tickets.findByBookingIdOrderByOrdinal(bookingId);
        for (int ordinal = 1; ordinal <= booking.quantity(); ordinal++) {
            final int position = ordinal;
            if (existing.stream().noneMatch(ticket -> ticket.toView().ordinal() == position)) {
                tickets.save(IssuedTicket.issue(bookingId, eventId, ordinal));
            }
        }
        tickets.flush();
        return tickets.findByBookingIdOrderByOrdinal(bookingId).stream().map(IssuedTicket::toView).toList();
    }

    public List<TicketView> ownedTickets(UUID bookingId, UUID userId) {
        var booking = bookings.lockOwned(bookingId, userId);
        // Idempotent backfill for confirmed orders created before automatic issuance.
        if (booking.active()) return issue(booking.eventId(), bookingId);
        return tickets.findByBookingIdOrderByOrdinal(bookingId).stream().map(IssuedTicket::toView).toList();
    }

    public TicketView checkIn(UUID eventId, UUID bookingId, UUID ticketId) {
        bookings.lockActive(eventId, bookingId);
        var ticket = tickets.findByIdAndBookingIdAndEventId(ticketId, bookingId, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ticket.checkIn(clock.instant());
        tickets.flush();
        return ticket.toView();
    }
}
