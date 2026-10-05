package com.team10.sems.event;

import com.team10.sems.event.internal.domain.Event;
import com.team10.sems.event.internal.persistence.EventRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class EventAccessService {

    private final EventRepository events;

    public EventAccessService(EventRepository events) {
        this.events = events;
    }

    public EventAccessView requireEvent(UUID eventId) {
        return findEvent(eventId).toAccessView();
    }

    public EventAccessView requireOwnedEvent(UUID eventId, UUID ownerId) {
        EventAccessView event = requireEvent(eventId);

        if (!event.organizerId().equals(ownerId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Event not found");
        }

        return event;
    }

    public EventAccessView requirePublishedEvent(UUID eventId) {
        EventAccessView event = requireEvent(eventId);

        if (!"PUBLISHED".equals(event.status())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Event not found");
        }

        return event;
    }

    private Event findEvent(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found"));
    }
}