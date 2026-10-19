package com.team10.sems.ticketing.internal.application;

import com.team10.sems.booking.BookingCancelled;
import com.team10.sems.ticketing.internal.persistence.IssuedTicketRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BookingCancellationListener {
    private final java.time.Clock clock;
    private final IssuedTicketRepository tickets;

    public BookingCancellationListener(IssuedTicketRepository tickets, java.time.Clock clock) { this.tickets = tickets; this.clock = clock; }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(BookingCancelled event) {
        var issued = tickets.findByBookingIdOrderByOrdinal(event.bookingId());
        if ("ATTENDEE_CANCELLED".equals(event.reason()) && issued.stream().anyMatch(t -> t.hasBeenUsed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An order with checked-in tickets cannot be cancelled");
        }
        var now = clock.instant();
        issued.forEach(ticket -> ticket.voidTicket(now));
        tickets.flush();
    }
}
