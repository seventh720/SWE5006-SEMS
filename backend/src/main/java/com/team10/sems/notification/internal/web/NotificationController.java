package com.team10.sems.notification.internal.web;

import com.team10.sems.notification.internal.application.NotificationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public NotificationService.NotificationPage list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        return notifications.list(UUID.fromString(jwt.getSubject()), page, size, unreadOnly);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        notifications.markRead(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }
}
