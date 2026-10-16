package com.team10.sems.booking.internal.application;

import com.team10.sems.event.EventAccessService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "sems.notifications.reminders-enabled", havingValue = "true", matchIfMissing = true)
public class RegistrationReminderScheduler {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RegistrationReminderScheduler.class);
    private final EventAccessService events;
    private final RegistrationReminderService reminders;
    private final Clock clock;

    public RegistrationReminderScheduler(EventAccessService events, RegistrationReminderService reminders, Clock clock) {
        this.events = events; this.reminders = reminders; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${sems.notifications.reminder-delay-ms:30000}", initialDelayString = "${sems.notifications.reminder-delay-ms:30000}")
    public void run() {
        for (var event : events.openingSoon(clock.instant())) {
            try { reminders.notifyEvent(event); }
            catch (RuntimeException failure) { LOG.error("Registration reminder failed for event {}", event, failure); }
        }
    }
}
