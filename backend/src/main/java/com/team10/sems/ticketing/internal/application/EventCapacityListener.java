package com.team10.sems.ticketing.internal.application;

import com.team10.sems.event.EventCapacityChanging;
import com.team10.sems.ticketing.internal.persistence.TicketTypeRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EventCapacityListener {
    private final TicketTypeRepository ticketTypes;

    public EventCapacityListener(TicketTypeRepository ticketTypes) {
        this.ticketTypes = ticketTypes;
    }

    @EventListener
    public void validate(EventCapacityChanging change) {
        if (ticketTypes.totalQuota(change.eventId()) > change.capacity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacity cannot be below allocated ticket quota");
        }
    }
}
