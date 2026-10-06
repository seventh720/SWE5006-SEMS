package com.team10.sems.booking.internal.web;

import com.team10.sems.booking.OrganizerBookingView;
import com.team10.sems.booking.internal.application.BookingService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}/bookings")
public class OrganizerBookingController {
    private final BookingService bookings;

    public OrganizerBookingController(BookingService bookings) { this.bookings = bookings; }

    @GetMapping
    public BookingService.BookingPage<OrganizerBookingView> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("eventId") UUID eventId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return bookings.organizerList(UUID.fromString(jwt.getSubject()), eventId, page, size);
    }
}
