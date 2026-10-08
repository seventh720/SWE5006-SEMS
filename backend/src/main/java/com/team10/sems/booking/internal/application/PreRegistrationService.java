package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.PreRegistrationView;
import com.team10.sems.booking.internal.domain.PreRegistration;
import com.team10.sems.booking.internal.persistence.PreRegistrationRepository;
import com.team10.sems.event.EventAccessService;
import com.team10.sems.ticketing.TicketReservationService;
import java.time.Instant;
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
@PreAuthorize("hasRole('ATTENDEE')")
public class PreRegistrationService {
    private final PreRegistrationRepository registrations;
    private final EventAccessService events;
    private final TicketReservationService tickets;

    public PreRegistrationService(PreRegistrationRepository registrations, EventAccessService events,
            TicketReservationService tickets) {
        this.registrations = registrations;
        this.events = events;
        this.tickets = tickets;
    }

    public PreRegistrationView save(UUID user, UUID eventId, BookingInput input) {
        if (!eventId.equals(input.eventId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event ID does not match the request");
        }
        var event = events.lockEvent(eventId);
        if (!"PUBLISHED".equals(event.status())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        if (event.registrationOpensAt() == null || !Instant.now().isBefore(event.registrationOpensAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pre-registration is only available before registration opens");
        }
        var ticket = tickets.inspect(eventId, input.ticketTypeId());
        if (ticket.priceMinor() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid ticket booking is not available yet");
        }
        if (input.quantity() > ticket.quota()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity exceeds the ticket quota");
        }
        BookingInfoValidator.validate(event.bookingRequirements(), input.attendeeInfo());
        var registration = registrations.findByUserIdAndEventId(user, eventId)
                .orElseGet(() -> PreRegistration.create(user, eventId));
        registration.update(input.ticketTypeId(), input.quantity(), input.attendeeInfo());
        return view(registrations.saveAndFlush(registration));
    }

    @Transactional(readOnly = true)
    public PreRegistrationView detail(UUID user, UUID eventId) { return view(owned(user, eventId)); }

    @Transactional(readOnly = true)
    public BookingService.BookingPage<PreRegistrationView> list(UUID user, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        var result = registrations.findByUserId(user, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
        return new BookingService.BookingPage<>(result.map(this::view).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    public void remove(UUID user, UUID eventId) {
        // Use the same event lock as saves and booking, without changing ticket inventory.
        events.lockEvent(eventId);
        registrations.delete(owned(user, eventId));
    }

    private PreRegistration owned(UUID user, UUID eventId) {
        return registrations.findByUserIdAndEventId(user, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pre-registration not found"));
    }

    private PreRegistrationView view(PreRegistration registration) {
        var event = events.requireEvent(registration.eventId());
        var ticket = tickets.inspect(registration.eventId(), registration.ticketTypeId());
        Instant now = Instant.now();
        String status = registration.bookingId() != null ? "BOOKED"
                : !"PUBLISHED".equals(event.status()) ? "EVENT_CANCELLED"
                : !now.isBefore(event.registrationClosesAt()) ? "CLOSED"
                : event.registrationOpensAt() != null && now.isBefore(event.registrationOpensAt()) ? "WAITING" : "OPEN";
        return new PreRegistrationView(registration.id(), event.id(), event.title(), registration.ticketTypeId(),
                ticket.name(), registration.quantity(), registration.attendeeInfo(), event.bookingRequirements().customFieldLabel(),
                event.registrationOpensAt(), event.registrationClosesAt(), status, registration.bookingId(), registration.updatedAt());
    }
}
