package com.team10.sems.ticketing.internal.web;

import com.team10.sems.ticketing.TicketTypeView;
import com.team10.sems.ticketing.internal.application.TicketTypeBrowseService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/ticket-types")
public class PublicTicketTypeController {

    private final TicketTypeBrowseService ticketTypes;

    public PublicTicketTypeController(
            TicketTypeBrowseService ticketTypes) {
        this.ticketTypes = ticketTypes;
    }

    @GetMapping
    public List<TicketTypeView> list(
            @PathVariable("eventId") UUID eventId) {

        return ticketTypes.list(eventId);
    }
}