package com.team10.sems.booking;

import com.team10.sems.booking.internal.persistence.BookingRepository;
import com.team10.sems.event.EventAccessService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Internal module API. Callers must authorize the user before using it. */
@Service
public class BookingAccessService {
    private final BookingRepository bookings;
    private final EventAccessService events;

    public BookingAccessService(BookingRepository bookings, EventAccessService events) {
        this.bookings = bookings;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ActiveBooking lockActive(UUID eventId, UUID bookingId) {
        var event = events.lockEvent(eventId);
        var booking = bookings.findById(bookingId)
                .filter(value -> value.eventId().equals(eventId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!"PUBLISHED".equals(event.status()) || booking.isCancelled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is no longer active");
        }
        return new ActiveBooking(bookingId, eventId, booking.quantity());
    }

    public record ActiveBooking(UUID id, UUID eventId, int quantity) { }
}
