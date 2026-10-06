package com.team10.sems.ticketing;

import com.team10.sems.event.EventAccessService;
import com.team10.sems.ticketing.internal.domain.TicketType;
import com.team10.sems.ticketing.internal.persistence.TicketTypeRepository;
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

    public TicketReservationService(
            TicketTypeRepository ticketTypes,
            EventAccessService events) {
        this.ticketTypes = ticketTypes;
        this.events = events;
    }

    public void reserve(
            UUID eventId,
            UUID ticketTypeId,
            int quantity) {

        events.requirePublishedEvent(eventId);

        TicketType ticketType = ticketTypes
                .findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket type not found"));

        try {
            ticketType.reserve(quantity);
            ticketTypes.flush();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception);
        }
    }
}