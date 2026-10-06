package com.team10.sems.ticketing;

import com.team10.sems.event.EventAccessService;
import com.team10.sems.ticketing.internal.domain.TicketType;
import com.team10.sems.ticketing.internal.persistence.TicketTypeRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TicketReservationService {
    private final TicketTypeRepository ticketTypes;
    private final EventAccessService events;

    public TicketReservationService(TicketTypeRepository ticketTypes, EventAccessService events) {
        this.ticketTypes = ticketTypes;
        this.events = events;
    }

    public TicketTypeView reserve(UUID eventId, UUID ticketTypeId, int quantity) {
        var event = events.lockEvent(eventId);
        if (!"PUBLISHED".equals(event.status())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        if (!event.startsAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event has already started");
        }
        TicketType ticket = find(eventId, ticketTypeId);
        try {
            ticket.reserve(quantity);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        ticketTypes.flush();
        return ticket.toManagementView();
    }

    public void release(UUID eventId, UUID ticketTypeId, int quantity) {
        events.lockEvent(eventId);
        find(eventId, ticketTypeId).release(quantity);
        ticketTypes.flush();
    }

    private TicketType find(UUID eventId, UUID ticketTypeId) {
        return ticketTypes.findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found"));
    }
}
