package com.team10.sems.event.internal.application;

import com.team10.sems.event.EventManagementView;
import com.team10.sems.event.EventCapacityChanging;
import com.team10.sems.event.EventCancelled;
import com.team10.sems.event.EventCopied;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher publisher;

    public EventManagementService(EventRepository events,
            ApplicationEventPublisher publisher) {
        this.events = events;
        this.publisher = publisher;
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
        Event event = Event.draft(owner, input.title().strip(), input.description().strip(),
                input.location().strip(), input.startsAt(), input.endsAt(), input.capacity());
        event.configureBooking(input.bookingRequirements());
        event.configureIllustration(input.illustration());
        event.configureRegistrationDeadline(input.registrationClosesAt());
        event.configureRegistrationOpening(input.registrationOpensAt());
        return events.saveAndFlush(event).toManagementView();
    }

    public EventManagementView update(UUID owner, UUID id, DraftInput input) {
        Event event = lockedOwned(owner, id);
        validateTime(input);
        if (input.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The current event version is required");
        }
        if ("PUBLISHED".equals(event.toAccessView().status()) && !input.endsAt().isAfter(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A published event must end in the future");
        }
        publisher.publishEvent(new EventCapacityChanging(id, input.capacity()));
        event.editDetails(input.title().strip(), input.description().strip(), input.location().strip(),
                input.startsAt(), input.endsAt(), input.capacity(), input.version());
        event.configureBooking(input.bookingRequirements());
        event.configureIllustration(input.illustration());
        event.configureRegistrationDeadline(input.registrationClosesAt());
        event.configureRegistrationOpening(input.registrationOpensAt());
        events.flush(); // Trigger optimistic-lock conflicts before constructing the response.
        return event.toManagementView();
    }

    public EventManagementView copy(UUID owner, UUID id) {
        var source = lockedOwned(owner, id).toManagementView();
        Event draft = Event.draft(owner, source.title(), source.description(), source.location(),
                source.startsAt(), source.endsAt(), source.capacity());
        draft.configureBooking(source.bookingRequirements());
        draft.configureIllustration(source.illustration());
        draft.configureRegistrationDeadline(source.registrationClosesAt());
        draft.configureRegistrationOpening(source.registrationOpensAt());
        var saved = events.saveAndFlush(draft).toManagementView();
        publisher.publishEvent(new EventCopied(id, saved.id()));
        return saved;
    }

    public EventManagementView publish(UUID owner, UUID id, long version) {
        Event event = lockedOwned(owner, id);
        event.publish(version, java.time.Instant.now());
        publisher.publishEvent(new com.team10.sems.event.EventPublishing(id));
        events.flush();
        return event.toManagementView();
    }

    public EventManagementView cancel(UUID owner, UUID id, long version) {
        Event event = lockedOwned(owner, id);
        event.cancel(version);
        publisher.publishEvent(new EventCancelled(id));
        events.flush();
        return event.toManagementView();
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(UUID owner) {
        return new DashboardSummary(
                events.countByOrganizerIdAndStatus(owner, "DRAFT"),
                events.countByOrganizerIdAndStatus(owner, "PUBLISHED"),
                events.countByOrganizerIdAndStatus(owner, "CANCELLED"),
                events.findTop4ByOrganizerIdAndStatusAndStartsAtAfterOrderByStartsAtAscIdAsc(
                        owner, "PUBLISHED", java.time.Instant.now()).stream().map(Event::toManagementView).toList());
    }

    public record DashboardSummary(long drafts, long published, long cancelled,
            List<EventManagementView> upcoming) { }

    private Event lockedOwned(UUID owner, UUID id) {
        Event event = events.lockById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        if (!event.toAccessView().organizerId().equals(owner)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        return event;
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
