package com.team10.sems.reporting.internal.web;

import com.team10.sems.reporting.EventStatistics;
import com.team10.sems.reporting.internal.application.ReportingService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}/statistics")
public class ReportingController {

    private final ReportingService reporting;

    public ReportingController(ReportingService reporting) {
        this.reporting = reporting;
    }

    @GetMapping
    public EventStatistics statistics(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("eventId") UUID eventId) {
        return reporting.statistics(UUID.fromString(jwt.getSubject()), eventId);
    }
}
