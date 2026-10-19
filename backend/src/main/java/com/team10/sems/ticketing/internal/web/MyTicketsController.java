package com.team10.sems.ticketing.internal.web;

import com.team10.sems.ticketing.TicketLifecycleService;
import com.team10.sems.ticketing.TicketView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/tickets")
public class MyTicketsController {
    private final TicketLifecycleService tickets;
    public MyTicketsController(TicketLifecycleService tickets) { this.tickets = tickets; }

    // POST because confirmed legacy orders can be issued their missing tickets here.
    @PostMapping
    @PreAuthorize("hasRole('ATTENDEE')")
    public List<TicketView> tickets(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        return tickets.ownedTickets(bookingId, UUID.fromString(jwt.getSubject()));
    }
}
