package com.team10.sems.event.internal.application;

import com.team10.sems.event.internal.domain.Event;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Set;
import java.time.LocalDate;
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
    private final java.time.Clock clock;
    private final EventRepository events;

    public EventBrowseService(EventRepository events, java.time.Clock clock) {
        this.clock = clock;
        this.events = events;
    }

    public EventPage browse(int page, int size, String keyword, String location, String status,
            LocalDate from, LocalDate to) {
        com.team10.sems.platform.query.QueryRange.page(page, size);
        var dates = com.team10.sems.platform.query.QueryRange.dates(from, to);
        if (!Set.of("ALL", "UPCOMING", "ONGOING", "ENDED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown event status filter");
        }
        var now = clock.instant();
        Specification<Event> filter = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("status"), "PUBLISHED"));
            predicates.add(cb.gt(cb.locate(cb.lower(root.get("title")), keyword.strip().toLowerCase(Locale.ROOT)), 0));
            predicates.add(cb.gt(cb.locate(cb.lower(root.get("location")), location.strip().toLowerCase(Locale.ROOT)), 0));
            if (dates.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), dates.from()));
            if (dates.until() != null) predicates.add(cb.lessThan(root.get("startsAt"), dates.until()));
            switch (status) {
                case "UPCOMING" -> predicates.add(cb.greaterThan(root.get("startsAt"), now));
                case "ONGOING" -> {
                    predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), now));
                    predicates.add(cb.greaterThan(root.get("endsAt"), now));
                }
                case "ENDED" -> predicates.add(cb.lessThanOrEqualTo(root.get("endsAt"), now));
                default -> { }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = events.findAll(filter, PageRequest.of(page, size, Sort.by("startsAt", "id")));
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
