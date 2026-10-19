package com.team10.sems.reporting.internal.web;

import com.team10.sems.reporting.internal.application.EventReportService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class EventReportController {
    private final EventReportService reports;
    public EventReportController(EventReportService reports) { this.reports = reports; }

    @GetMapping("/api/v1/organizer/reports")
    public EventReportService.ReportPage owned(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name="eventId", required=false) UUID event,
            @RequestParam(name="keyword", defaultValue="") String keyword,
            @RequestParam(name="ticketType", defaultValue="") String ticketType,
            @RequestParam(name="bookingStatus", defaultValue="ALL") String bookingStatus,
            @RequestParam(name="from", required=false) LocalDate from,
            @RequestParam(name="to", required=false) LocalDate to,
            @RequestParam(name="page", defaultValue="0") int page,
            @RequestParam(name="size", defaultValue="10") int size) {
        return reports.owned(UUID.fromString(jwt.getSubject()), event, keyword, ticketType, bookingStatus, from, to, page, size);
    }

    @GetMapping("/api/v1/admin/reports")
    public EventReportService.ReportPage all(
            @RequestParam(name="eventId", required=false) UUID event,
            @RequestParam(name="keyword", defaultValue="") String keyword,
            @RequestParam(name="ticketType", defaultValue="") String ticketType,
            @RequestParam(name="bookingStatus", defaultValue="ALL") String bookingStatus,
            @RequestParam(name="from", required=false) LocalDate from,
            @RequestParam(name="to", required=false) LocalDate to,
            @RequestParam(name="page", defaultValue="0") int page,
            @RequestParam(name="size", defaultValue="10") int size) {
        return reports.all(event, keyword, ticketType, bookingStatus, from, to, page, size);
    }
}
