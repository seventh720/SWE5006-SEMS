package com.team10.sems.event.internal.application;

import com.team10.sems.event.EventView;
import com.team10.sems.event.internal.persistence.EventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class EventBrowseService {
    private final EventRepository events;

    public EventBrowseService(EventRepository events) {
        this.events = events;
    }

    public EventPage browse(int page, int size, String keyword) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination: page must be non-negative and size must be 1–50");
        }
        var result = events.findPublished(keyword == null ? "" : keyword.strip(),
                PageRequest.of(page, size, Sort.by("startsAt", "id")));
        return new EventPage(result.map(event -> event.toView()).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    public EventView detail(UUID id) {
        return events.findByIdAndStatus(id, "PUBLISHED")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"))
                .toView();
    }

    public record EventPage(List<EventView> items, int page, int size, long totalElements, int totalPages) { }
}
