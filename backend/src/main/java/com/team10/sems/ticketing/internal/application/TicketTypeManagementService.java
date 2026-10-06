package com.team10.sems.ticketing.internal.application;

import com.team10.sems.event.EventAccessService;
import com.team10.sems.event.EventAccessView;
import com.team10.sems.ticketing.TicketTypeView;
import com.team10.sems.ticketing.internal.domain.TicketType;
import com.team10.sems.ticketing.internal.persistence.TicketTypeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
public class TicketTypeManagementService {

    private final TicketTypeRepository ticketTypes;
    private final EventAccessService events;

    public TicketTypeManagementService(
            TicketTypeRepository ticketTypes,
            EventAccessService events) {
        this.ticketTypes = ticketTypes;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TicketTypeView> list(UUID owner, UUID eventId) {
        events.requireOwnedEvent(eventId, owner);

        return ticketTypes
                .findByEventIdOrderByCreatedAtAscIdAsc(eventId)
                .stream()
                .map(TicketType::toManagementView)
                .toList();
    }

    public TicketTypeView create(
            UUID owner,
            UUID eventId,
            TicketTypeInput input) {

        EventAccessView event =
                events.lockOwnedEvent(eventId, owner);

        requireEditableEvent(event);

        try {
            requireCapacity(event, ticketTypes.totalQuota(eventId) + input.quota());
            TicketType ticketType = TicketType.create(
                    eventId,
                    input.name(),
                    input.priceMinor(),
                    input.currency(),
                    input.quota());

            return ticketTypes
                    .saveAndFlush(ticketType)
                    .toManagementView();

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception);
        }
    }

    public TicketTypeView update(
            UUID owner,
            UUID eventId,
            UUID ticketTypeId,
            TicketTypeInput input) {

        EventAccessView event =
                events.lockOwnedEvent(eventId, owner);

        requireEditableEvent(event);

        if (input.version() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The current ticket type version is required");
        }

        TicketType ticketType = ticketTypes
                .findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket type not found"));

        try {
            requireCapacity(event, ticketTypes.totalQuota(eventId) - ticketType.getQuota() + input.quota());
            ticketType.edit(
                    input.name(),
                    input.priceMinor(),
                    input.currency(),
                    input.quota(),
                    input.version());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception);
        }

        ticketTypes.flush();

        return ticketType.toManagementView();
    }

    private void requireCapacity(EventAccessView event, long total) {
        if (total > event.capacity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total ticket quota exceeds event capacity");
        }
    }

    private void requireEditableEvent(EventAccessView event) {
        if ((!"DRAFT".equals(event.status()) && !"PUBLISHED".equals(event.status()))
                || !event.startsAt().isAfter(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ticket types can only be configured before an active event starts");
        }
    }
}
