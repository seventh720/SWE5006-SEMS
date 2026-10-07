package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.BookingView;
import com.team10.sems.booking.AttendeeInfo;
import com.team10.sems.event.BookingRequirements;
import com.team10.sems.booking.OrganizerBookingView;
import com.team10.sems.booking.internal.domain.Booking;
import com.team10.sems.booking.internal.persistence.BookingRepository;
import com.team10.sems.booking.internal.persistence.BookingRequestLock;
import com.team10.sems.event.EventAccessService;
import com.team10.sems.ticketing.TicketReservationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BookingService {
    private final BookingRepository bookings;
    private final BookingRequestLock requestLocks;
    private final EventAccessService events;
    private final TicketReservationService tickets;

    public BookingService(BookingRepository bookings, BookingRequestLock requestLocks,
            EventAccessService events, TicketReservationService tickets) {
        this.bookings = bookings;
        this.requestLocks = requestLocks;
        this.events = events;
        this.tickets = tickets;
    }

    @PreAuthorize("hasRole('ATTENDEE')")
    public Created create(UUID user, String key, BookingInput input) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1–100 characters");
        }
        requestLocks.acquire(user, key);
        var previous = bookings.findByUserIdAndRequestKey(user, key);
        if (previous.isPresent()) {
            Booking booking = previous.get();
            if (!booking.matches(input.eventId(), input.ticketTypeId(), input.quantity(), input.attendeeInfo())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used with different parameters");
            }
            return new Created(booking.toView(), false);
        }
        var event = events.lockEvent(input.eventId());
        var ticket = tickets.reserve(input.eventId(), input.ticketTypeId(), input.quantity());
        validateInfo(event.bookingRequirements(), input.attendeeInfo());
        Booking booking = Booking.confirmed(user, key, event, ticket, input.quantity(), input.attendeeInfo());
        return new Created(bookings.saveAndFlush(booking).toView(), true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ATTENDEE')")
    public BookingPage<BookingView> list(UUID user, int page, int size) {
        return page(bookings.findByUserId(user, pageable(page, size)).map(Booking::toView));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ATTENDEE')")
    public BookingView detail(UUID user, UUID id) { return owned(user, id).toView(); }

    @PreAuthorize("hasRole('ATTENDEE')")
    public BookingView cancel(UUID user, UUID id) {
        UUID eventId = bookings.findOwnedEventId(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        var event = events.lockEvent(eventId);
        Booking booking = owned(user, id);
        if (booking.isCancelled()) return booking.toView();
        if (!event.startsAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event has already started");
        }
        if (booking.cancel("ATTENDEE_CANCELLED")) {
            tickets.release(eventId, booking.ticketTypeId(), booking.quantity());
        }
        bookings.flush();
        return booking.toView();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public BookingPage<OrganizerBookingView> organizerList(UUID owner, UUID eventId, int page, int size) {
        events.requireOwnedEvent(eventId, owner);
        return page(bookings.findByEventId(eventId, pageable(page, size)).map(Booking::toOrganizerView));
    }

    private void validateInfo(BookingRequirements requirements,
            AttendeeInfo info) {
        checkField(requirements.realName(), info.realName(), "Real name");
        checkField(requirements.email(), info.email(), "Email");
        checkField(requirements.phone(), info.phone(), "Phone");
        checkField(requirements.studentId(), info.studentId(), "Student ID number");
        checkField(requirements.passport(), info.passportNumber(), "Passport number");
        checkField(requirements.customFieldLabel() != null, info.customAnswer(), "Custom information");
    }

    private void checkField(boolean required, String value, String label) {
        if (required && value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required for this event");
        }
        if (!required && value != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is not requested for this event");
        }
    }

    private Booking owned(UUID user, UUID id) {
        return bookings.findByIdAndUserId(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private <T> BookingPage<T> page(Page<T> page) {
        return new BookingPage<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public record Created(BookingView booking, boolean created) { }
    public record BookingPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) { }
}
