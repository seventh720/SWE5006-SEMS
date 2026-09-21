package com.team10.sems.event.internal.web;

import com.team10.sems.event.EventView;
import com.team10.sems.event.internal.application.EventBrowseService;
import com.team10.sems.event.internal.application.EventBrowseService.EventPage;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class PublicEventController {
    private final EventBrowseService events;

    public PublicEventController(EventBrowseService events) {
        this.events = events;
    }

    @GetMapping
    public EventPage browse(@RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "keyword", defaultValue = "") String keyword) {
        return events.browse(page, size, keyword);
    }

    @GetMapping("/{id}")
    public EventView detail(@PathVariable("id") UUID id) {
        return events.detail(id);
    }
}
