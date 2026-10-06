package com.team10.sems.booking.internal.web;

import com.team10.sems.booking.BookingView;
import com.team10.sems.booking.internal.application.BookingInput;
import com.team10.sems.booking.internal.application.BookingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) { this.bookings = bookings; }

    @PostMapping
    public ResponseEntity<BookingView> create(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody BookingInput input) {
        var result = bookings.create(UUID.fromString(jwt.getSubject()), key, input);
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/bookings/" + result.booking().id())).body(result.booking())
                : ResponseEntity.ok(result.booking());
    }

    @GetMapping
    public BookingService.BookingPage<BookingView> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return bookings.list(UUID.fromString(jwt.getSubject()), page, size);
    }

    @GetMapping("/{id}")
    public BookingView detail(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return bookings.detail(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping("/{id}/cancel")
    public BookingView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return bookings.cancel(UUID.fromString(jwt.getSubject()), id);
    }
}
