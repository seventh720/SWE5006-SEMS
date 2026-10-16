package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.internal.persistence.PreRegistrationRepository;
import com.team10.sems.event.EventAccessService;
import com.team10.sems.notification.NotificationDeliveryService;
import com.team10.sems.notification.NotificationType;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationReminderService {
    private final EventAccessService events;
    private final PreRegistrationRepository registrations;
    private final NotificationDeliveryService notifications;
    private final Clock clock;

    public RegistrationReminderService(EventAccessService events, PreRegistrationRepository registrations,
            NotificationDeliveryService notifications, Clock clock) {
        this.events = events; this.registrations = registrations; this.notifications = notifications; this.clock = clock;
    }

    @Transactional
    public void notifyEvent(UUID eventId) {
        // Lock before re-reading the time/window and recipients, serialized with edit, deletion and booking.
        var event = events.lockEvent(eventId);
        var now = clock.instant();
        var opens = event.registrationOpensAt();
        if (!"PUBLISHED".equals(event.status()) || opens == null || !opens.isAfter(now)
                || opens.isAfter(now.plusSeconds(300)) || !event.registrationClosesAt().isAfter(now)
                || !event.startsAt().isAfter(now)) return;
        String time = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'SGT'")
                .withZone(ZoneId.of("Asia/Singapore")).format(opens);
        for (var registration : registrations.findByEventIdAndBookingIdIsNull(eventId)) {
            notifications.send(registration.userId(), NotificationType.REGISTRATION_OPENING_SOON,
                    registration.id() + ":" + opens, "Registration opens soon",
                    event.title() + ": registration opens at " + time + ". Confirm your booking when registration opens. Pre-registration does not reserve tickets.",
                    eventId, null, registration.ticketTypeId());
        }
    }
}
