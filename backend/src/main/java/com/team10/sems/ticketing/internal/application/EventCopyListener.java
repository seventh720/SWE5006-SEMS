package com.team10.sems.ticketing.internal.application;

import com.team10.sems.event.EventCopied;
import com.team10.sems.ticketing.internal.domain.TicketType;
import com.team10.sems.ticketing.internal.persistence.TicketTypeRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventCopyListener {
    private final TicketTypeRepository tickets;

    public EventCopyListener(TicketTypeRepository tickets) { this.tickets = tickets; }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void copy(EventCopied event) {
        var copies = tickets.findByEventIdOrderByCreatedAtAscIdAsc(event.sourceId()).stream()
                .map(ticket -> TicketType.create(event.draftId(), ticket.getName(), ticket.getPriceMinor(),
                        ticket.getCurrency(), ticket.getQuota())).toList();
        tickets.saveAllAndFlush(copies);
    }
}
