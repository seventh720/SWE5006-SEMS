package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.AttendeeInfo;
import com.team10.sems.booking.internal.domain.Booking;
import com.team10.sems.booking.internal.persistence.*;
import com.team10.sems.event.*;
import com.team10.sems.ticketing.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingRequestLock locks = mock(BookingRequestLock.class);
    private final EventAccessService events = mock(EventAccessService.class);
    private final TicketReservationService tickets = mock(TicketReservationService.class);
    private final PreRegistrationRepository preRegistrations = mock(PreRegistrationRepository.class);
    private final BookingService service = new BookingService(bookings, locks, events, tickets, preRegistrations);
    private final UUID user = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();

    private EventAccessView event(Instant startsAt, BookingRequirements requirements) {
        return new EventAccessView(eventId, UUID.randomUUID(), startsAt, startsAt.plusSeconds(3600),
                10, "PUBLISHED", "Event", "Venue", requirements, startsAt, null);
    }

    private Booking booking(EventAccessView event) {
        return Booking.confirmed(user, "key", event,
                new TicketTypeView(ticketId, eventId, "Free", 0, "SGD", 10, 1, 0L, true), 1, AttendeeInfo.EMPTY);
    }

    @Test
    void invalidInfoNeverReservesInventoryOrWritesAnOrder() {
        when(events.lockEvent(eventId)).thenReturn(event(Instant.now().plusSeconds(3600),
                new BookingRequirements(true, false, false, false, false, null)));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.create(user, "key", new BookingInput(eventId, ticketId, 1, null))).getStatusCode().value());
        verifyNoInteractions(tickets, preRegistrations);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void matchingRetryReturnsExistingOrderWithoutReservingAgain() {
        var event = event(Instant.now().plusSeconds(3600), BookingRequirements.NONE);
        var booking = booking(event);
        when(bookings.findByUserIdAndRequestKey(user, "key")).thenReturn(Optional.of(booking));
        when(events.requireEvent(eventId)).thenReturn(event);
        var result = service.create(user, "key", new BookingInput(eventId, ticketId, 1, null));
        assertFalse(result.created());
        assertEquals(booking.toView().id(), result.booking().id());
        verifyNoInteractions(tickets);
        verify(events, never()).lockEvent(any());
    }

    @Test
    void changedRetryParametersAreRejected() {
        var booking = booking(event(Instant.now().plusSeconds(3600), BookingRequirements.NONE));
        when(bookings.findByUserIdAndRequestKey(user, "key")).thenReturn(Optional.of(booking));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.create(user, "key", new BookingInput(eventId, ticketId, 2, null))).getStatusCode().value());
        verifyNoInteractions(tickets, events);
    }

    @Test
    void cancellationReleasesInventoryOnlyOnce() {
        var event = event(Instant.now().plusSeconds(3600), BookingRequirements.NONE);
        var booking = booking(event);
        UUID id = booking.toView().id();
        when(bookings.findOwnedEventId(id, user)).thenReturn(Optional.of(eventId));
        when(bookings.findByIdAndUserId(id, user)).thenReturn(Optional.of(booking));
        when(events.lockEvent(eventId)).thenReturn(event);
        when(events.requireEvent(eventId)).thenReturn(event);
        assertEquals("CANCELLED", service.cancel(user, id).status());
        service.cancel(user, id);
        verify(tickets, times(1)).release(eventId, ticketId, 1);
    }

    @Test
    void cancellationAfterStartLeavesOrderAndInventoryUnchanged() {
        var event = event(Instant.now().minusSeconds(1), BookingRequirements.NONE);
        var booking = booking(event);
        UUID id = booking.toView().id();
        when(bookings.findOwnedEventId(id, user)).thenReturn(Optional.of(eventId));
        when(bookings.findByIdAndUserId(id, user)).thenReturn(Optional.of(booking));
        when(events.lockEvent(eventId)).thenReturn(event);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.cancel(user, id)).getStatusCode().value());
        assertFalse(booking.isCancelled());
        verifyNoInteractions(tickets);
    }
}
