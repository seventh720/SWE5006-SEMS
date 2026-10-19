package com.team10.sems.ticketing.internal.application;

import com.team10.sems.booking.BookingConfirmed;
import com.team10.sems.ticketing.TicketLifecycleService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingConfirmationListener {
    private final TicketLifecycleService tickets;
    public BookingConfirmationListener(TicketLifecycleService tickets) { this.tickets = tickets; }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmed(BookingConfirmed event) {
        tickets.issue(event.eventId(), event.bookingId());
    }
}
