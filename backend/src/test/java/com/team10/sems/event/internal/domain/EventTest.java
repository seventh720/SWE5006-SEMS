package com.team10.sems.event.internal.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EventTest {
    @Test
    void publicationRequiresStartStrictlyAfterCurrentInstant() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Location", now, now.plusSeconds(3600), 10);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> event.publish(0, now)).getStatusCode().value());
        event.publish(0, now.minusSeconds(1));
        assertEquals("PUBLISHED", event.toView().status());
    }
    @Test
    void publicationRequiresDeadlineStrictlyAfterNowAndNoLaterThanStart() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Location", now.plusSeconds(3600), now.plusSeconds(7200), 10);
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationDeadline(now.plusSeconds(3601)));
        event.configureRegistrationDeadline(now);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> event.publish(0, now)).getStatusCode().value());
        event.configureRegistrationDeadline(now.plusSeconds(1));
        event.publish(0, now);
        assertEquals(now.plusSeconds(1), event.toView().registrationClosesAt());
    }

    @Test
    void openingCanChangeBeforeRegistrationButIsFixedOnceOpen() {
        Instant now = Instant.now();
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Location", now.plusSeconds(3600), now.plusSeconds(7200), 10);
        event.configureRegistrationOpening(now.plusSeconds(600));
        event.publish(0, now);
        event.configureRegistrationOpening(now.plusSeconds(1200));
        assertEquals(now.plusSeconds(1200), event.toView().registrationOpensAt());
        event.configureRegistrationOpening(now.minusSeconds(60));
        event.configureRegistrationOpening(now.minusSeconds(60));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> event.configureRegistrationOpening(now.plusSeconds(600))).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(null));
    }

    @Test
    void immediateOpeningIsFixedAfterPublication() {
        Instant now = Instant.now();
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Location", now.plusSeconds(3600), now.plusSeconds(7200), 10);
        event.publish(0, now);
        event.configureRegistrationOpening(null);
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(now.plusSeconds(600)));
    }

    @Test
    void registrationWindowRejectsEqualOrReversedBoundaries() {
        Instant start = Instant.parse("2035-01-01T00:00:00Z");
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Venue", start, start.plusSeconds(3600), 10);
        event.configureRegistrationDeadline(start.minusSeconds(60));
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(start.minusSeconds(60)));
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(start));
        event.configureRegistrationOpening(start.minusSeconds(61));
    }

    @Test
    void cancelledEventCannotBeEditedOrPublishedAgain() {
        Instant now = Instant.now();
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Venue", now.plusSeconds(3600), now.plusSeconds(7200), 10);
        assertThrows(ResponseStatusException.class, () -> event.cancel(1));
        event.publish(0, now);
        assertThrows(ResponseStatusException.class, () -> event.publish(0, now));
        event.cancel(0);
        assertThrows(ResponseStatusException.class, () -> event.cancel(0));
        assertThrows(ResponseStatusException.class, () -> event.editDetails("New", "Details", "Venue", now, now.plusSeconds(3600), 10, 0));
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(null));
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationDeadline(now));
        assertEquals("CANCELLED", event.toView().status());
    }

}
