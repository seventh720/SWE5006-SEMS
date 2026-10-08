package com.team10.sems.booking.internal.web;

import com.team10.sems.booking.PreRegistrationView;
import com.team10.sems.booking.internal.application.BookingInput;
import com.team10.sems.booking.internal.application.BookingService;
import com.team10.sems.booking.internal.application.PreRegistrationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pre-registrations")
public class PreRegistrationController {
    private final PreRegistrationService registrations;

    public PreRegistrationController(PreRegistrationService registrations) { this.registrations = registrations; }

    @GetMapping
    public BookingService.BookingPage<PreRegistrationView> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return registrations.list(UUID.fromString(jwt.getSubject()), page, size);
    }

    @GetMapping("/{eventId}")
    public PreRegistrationView detail(@AuthenticationPrincipal Jwt jwt, @PathVariable("eventId") UUID eventId) {
        return registrations.detail(UUID.fromString(jwt.getSubject()), eventId);
    }

    @PutMapping("/{eventId}")
    public PreRegistrationView save(@AuthenticationPrincipal Jwt jwt, @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody BookingInput input) {
        return registrations.save(UUID.fromString(jwt.getSubject()), eventId, input);
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable("eventId") UUID eventId) {
        registrations.remove(UUID.fromString(jwt.getSubject()), eventId);
    }
}
