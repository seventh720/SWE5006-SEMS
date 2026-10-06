package com.team10.sems.ticketing.internal.application;

import com.team10.sems.event.EventAccessService;
import com.team10.sems.ticketing.TicketTypeView;
import com.team10.sems.ticketing.internal.domain.TicketType;
import com.team10.sems.ticketing.internal.persistence.TicketTypeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketTypeBrowseService {

    private final TicketTypeRepository ticketTypes;
    private final EventAccessService events;

    public TicketTypeBrowseService(
            TicketTypeRepository ticketTypes,
            EventAccessService events) {
        this.ticketTypes = ticketTypes;
        this.events = events;
    }

    public List<TicketTypeView> list(UUID eventId) {
        events.requirePublishedEvent(eventId);

        return ticketTypes
                .findByEventIdOrderByCreatedAtAscIdAsc(eventId)
                .stream()
                .map(TicketType::toPublicView)
                .toList();
    }
}
