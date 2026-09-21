package com.team10.sems.event.internal.web;

import com.team10.sems.event.EventManagementView;
import com.team10.sems.event.internal.application.DraftInput;
import com.team10.sems.event.internal.application.EventManagementService;
import com.team10.sems.event.internal.application.EventManagementService.ManagementPage;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizer/events")
public class OrganizerEventController {
    private final EventManagementService events;

    public OrganizerEventController(EventManagementService events) {
        this.events = events;
    }

    @GetMapping
    public ManagementPage list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size) {
        return events.list(UUID.fromString(jwt.getSubject()), page, size);
    }

    @GetMapping("/{id}")
    public EventManagementView detail(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return events.detail(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping
    public ResponseEntity<EventManagementView> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DraftInput input) {
        var created = events.create(UUID.fromString(jwt.getSubject()), input);
        return ResponseEntity.created(URI.create("/api/v1/organizer/events/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public EventManagementView update(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody DraftInput input) {
        return events.update(UUID.fromString(jwt.getSubject()), id, input);
    }
    public record VersionRequest(
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long version) { }

    @PostMapping("/{id}/publish")
    public EventManagementView publish(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody VersionRequest input) {
        return events.publish(UUID.fromString(jwt.getSubject()), id, input.version());
    }

    @PostMapping("/{id}/cancel")
    public EventManagementView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody VersionRequest input) {
        return events.cancel(UUID.fromString(jwt.getSubject()), id, input.version());
    }

}
