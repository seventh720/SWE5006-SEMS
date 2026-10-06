package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.internal.persistence.BookingRepository;
import com.team10.sems.event.EventCancelled;
import com.team10.sems.ticketing.TicketReservationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventCancellationListener {
    private final BookingRepository bookings;
    private final TicketReservationService tickets;

    public EventCancellationListener(BookingRepository bookings, TicketReservationService tickets) {
        this.bookings = bookings;
        this.tickets = tickets;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(EventCancelled event) {
        // Synchronous: the publisher holds the event lock and all changes roll back together.
        for (var booking : bookings.findByEventIdAndStatusOrderByTicketTypeIdAscIdAsc(event.eventId(), "CONFIRMED")) {
            if (booking.cancel("EVENT_CANCELLED")) {
                tickets.release(event.eventId(), booking.ticketTypeId(), booking.quantity());
            }
        }
        bookings.flush();
    }
}
