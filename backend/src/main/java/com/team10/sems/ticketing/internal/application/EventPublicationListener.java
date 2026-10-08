package com.team10.sems.ticketing.internal.application;

import com.team10.sems.event.EventPublishing;
import com.team10.sems.ticketing.internal.persistence.TicketTypeRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EventPublicationListener {
    private final TicketTypeRepository tickets;
    public EventPublicationListener(TicketTypeRepository tickets) { this.tickets = tickets; }
    @EventListener
    public void validate(EventPublishing event) {
        if (tickets.findByEventIdOrderByCreatedAtAscIdAsc(event.eventId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one ticket type before publishing. You can create a free ticket using the event capacity.");
        }
    }
}
