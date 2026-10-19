package com.team10.sems.booking.internal.application;

import com.team10.sems.checkin.StaffAssignmentService;
import com.team10.sems.booking.BookingCancelled;
import com.team10.sems.booking.BookingConfirmed;
import com.team10.sems.booking.internal.persistence.BookingRepository;
import com.team10.sems.booking.internal.persistence.PreRegistrationRepository;
import com.team10.sems.event.EventAccessService;
import com.team10.sems.event.EventCancelled;
import com.team10.sems.event.EventUpdated;
import com.team10.sems.notification.NotificationDeliveryService;
import com.team10.sems.notification.NotificationType;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class BookingNotificationListener {
    private final StaffAssignmentService staff;
    private final BookingRepository bookings;
    private final PreRegistrationRepository registrations;
    private final NotificationDeliveryService notifications;
    private final EventAccessService events;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'SGT'")
            .withZone(ZoneId.of("Asia/Singapore"));

    public BookingNotificationListener(BookingRepository bookings, PreRegistrationRepository registrations,
            NotificationDeliveryService notifications, EventAccessService events, StaffAssignmentService staff) {
        this.staff = staff;
        this.bookings = bookings; this.registrations = registrations; this.notifications = notifications; this.events = events;
    }

    @EventListener
    public void confirmed(BookingConfirmed event) {
        var booking = bookings.findById(event.bookingId()).orElseThrow();
        notifications.send(booking.userId(), NotificationType.BOOKING_CONFIRMED, event.bookingId().toString(),
                "Booking confirmed", booking.toView().eventTitle() + ": " + booking.quantity() + " ticket(s) confirmed.",
                booking.eventId(), event.bookingId(), booking.ticketTypeId());
    }

    @EventListener
    public void cancelled(BookingCancelled event) {
        var booking = bookings.findById(event.bookingId()).orElseThrow();
        notifications.send(booking.userId(), NotificationType.BOOKING_CANCELLED, event.bookingId().toString(),
                "Booking cancelled", booking.toView().eventTitle() + ("EVENT_CANCELLED".equals(event.reason())
                        ? ": the organizer cancelled this event. Your booking has been cancelled."
                        : ": your booking has been cancelled."),
                booking.eventId(), event.bookingId(), booking.ticketTypeId());
    }

    @EventListener
    public void eventCancelled(EventCancelled event) {
        String title = events.requireEvent(event.eventId()).title();
        var bookers = new java.util.HashSet<>(bookings.eventCancellationRecipients(event.eventId()));
        for (UUID user : staff.recipients(event.eventId())) {
            if (!bookers.contains(user)) notifications.send(user, NotificationType.EVENT_CANCELLED, event.eventId().toString(),
                    "Event cancelled", title + ": the organizer cancelled this event. Staff check-in is no longer available.",
                    event.eventId(), null, null);
        }
        for (var registration : registrations.findByEventIdAndBookingIdIsNull(event.eventId())) {
            notifications.send(registration.userId(), NotificationType.EVENT_CANCELLED, event.eventId().toString(),
                    "Event cancelled", title + ": the organizer cancelled this event. Your pre-registration is no longer available.",
                    event.eventId(), null, registration.ticketTypeId());
        }
    }

    @EventListener
    public void updated(EventUpdated event) {
        var before = event.before(); var after = event.after();
        if (!"PUBLISHED".equals(before.status())) return;
        var changes = new java.util.ArrayList<String>();
        if (!before.title().equals(after.title())) changes.add("Title: " + before.title() + " → " + after.title());
        if (!before.location().equals(after.location())) changes.add("Location: " + before.location() + " → " + after.location());
        if (!before.startsAt().equals(after.startsAt())) changes.add("Starts: " + TIME.format(before.startsAt()) + " → " + TIME.format(after.startsAt()));
        if (!before.endsAt().equals(after.endsAt())) changes.add("Ends: " + TIME.format(before.endsAt()) + " → " + TIME.format(after.endsAt()));
        if (!Objects.equals(before.registrationOpensAt(), after.registrationOpensAt())) changes.add("Registration opens: " + time(before.registrationOpensAt()) + " → " + time(after.registrationOpensAt()));
        if (!Objects.equals(before.registrationClosesAt(), after.registrationClosesAt())) changes.add("Registration closes: " + time(before.registrationClosesAt()) + " → " + time(after.registrationClosesAt()));
        if (!before.description().equals(after.description())) changes.add("The event description has been updated. Please review the event details.");
        if (changes.isEmpty()) return;
        // Prefer an order link for bookers; notify each user once even with several orders.
        var recipients = new LinkedHashMap<UUID, UUID>();
        for (var booking : bookings.findByEventIdAndStatusOrderByTicketTypeIdAscIdAsc(after.id(), "CONFIRMED")) {
            recipients.putIfAbsent(booking.userId(), booking.toView().id());
        }
        for (var registration : registrations.findByEventIdAndBookingIdIsNull(after.id())) {
            recipients.putIfAbsent(registration.userId(), null);
        }
        for (UUID user : staff.recipients(after.id())) recipients.putIfAbsent(user, null);
        recipients.forEach((user, booking) -> notifications.send(user, NotificationType.EVENT_UPDATED,
                after.id() + ":" + after.version(), "Event updated", after.title() + "\n" + String.join("\n", changes),
                after.id(), booking, null));
    }

    private String time(java.time.Instant value) { return value == null ? "Not scheduled" : TIME.format(value); }
}
