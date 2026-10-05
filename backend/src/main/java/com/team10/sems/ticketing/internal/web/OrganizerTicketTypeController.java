package com.team10.sems.ticketing.internal.web;

import com.team10.sems.ticketing.TicketTypeView;
import com.team10.sems.ticketing.internal.application.TicketTypeInput;
import com.team10.sems.ticketing.internal.application.TicketTypeManagementService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}/ticket-types")
public class OrganizerTicketTypeController {

    private final TicketTypeManagementService ticketTypes;

    public OrganizerTicketTypeController(
            TicketTypeManagementService ticketTypes) {
        this.ticketTypes = ticketTypes;
    }

    @GetMapping
    public List<TicketTypeView> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("eventId") UUID eventId) {

        return ticketTypes.list(
                UUID.fromString(jwt.getSubject()),
                eventId);
    }

    @PostMapping
    public ResponseEntity<TicketTypeView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody TicketTypeInput input) {

        TicketTypeView created = ticketTypes.create(
                UUID.fromString(jwt.getSubject()),
                eventId,
                input);

        return ResponseEntity
                .created(URI.create(
                        "/api/v1/organizer/events/"
                                + eventId
                                + "/ticket-types/"
                                + created.id()))
                .body(created);
    }

    @PutMapping("/{ticketTypeId}")
    public TicketTypeView update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("eventId") UUID eventId,
            @PathVariable("ticketTypeId") UUID ticketTypeId,
            @Valid @RequestBody TicketTypeInput input) {

        return ticketTypes.update(
                UUID.fromString(jwt.getSubject()),
                eventId,
                ticketTypeId,
                input);
    }
}