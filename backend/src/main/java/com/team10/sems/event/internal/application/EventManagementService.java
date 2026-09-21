package com.team10.sems.event.internal.application;

import com.team10.sems.event.EventManagementView;
import com.team10.sems.event.internal.domain.Event;
import com.team10.sems.event.internal.persistence.EventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
public class EventManagementService {
    private final EventRepository events;

    public EventManagementService(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public ManagementPage list(UUID owner, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination: page must be non-negative and size must be 1–50");
        }
        var result = events.findByOrganizerId(owner,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new ManagementPage(result.map(Event::toManagementView).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public EventManagementView detail(UUID owner, UUID id) {
        return owned(owner, id).toManagementView();
    }

    public EventManagementView create(UUID owner, DraftInput input) {
        validateTime(input);
        return events.saveAndFlush(Event.draft(owner, input.title().strip(), input.description().strip(),
                input.location().strip(), input.startsAt(), input.endsAt(), input.capacity())).toManagementView();
    }

    public EventManagementView update(UUID owner, UUID id, DraftInput input) {
        Event event = owned(owner, id);
        validateTime(input);
        if (input.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The current event version is required");
        }
        event.editDraft(input.title().strip(), input.description().strip(), input.location().strip(),
                input.startsAt(), input.endsAt(), input.capacity(), input.version());
        events.flush(); // Trigger optimistic-lock conflicts before constructing the response.
        return event.toManagementView();
    }

    public EventManagementView publish(UUID owner, UUID id, long version) {
        Event event = owned(owner, id);
        event.publish(version, java.time.Instant.now());
        events.flush();
        return event.toManagementView();
    }

    public EventManagementView cancel(UUID owner, UUID id, long version) {
        Event event = owned(owner, id);
        event.cancel(version);
        events.flush();
        return event.toManagementView();
    }

    private Event owned(UUID owner, UUID id) {
        return events.findByIdAndOrganizerId(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    private void validateTime(DraftInput input) {
        if (!input.startsAt().isBefore(input.endsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
    }

    public record ManagementPage(List<EventManagementView> items, int page, int size,
            long totalElements, int totalPages) { }
}
