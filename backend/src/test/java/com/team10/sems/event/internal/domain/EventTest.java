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
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Location", now.plusSeconds(3600), now.plusSeconds(7200), 10);
        event.configureRegistrationOpening(now.plusSeconds(600), Instant.parse("2030-01-01T00:00:00Z"));
        event.publish(0, now);
        event.configureRegistrationOpening(now.plusSeconds(1200), Instant.parse("2030-01-01T00:00:00Z"));
        assertEquals(now.plusSeconds(1200), event.toView().registrationOpensAt());
        event.configureRegistrationOpening(now.minusSeconds(60), Instant.parse("2030-01-01T00:00:00Z"));
        event.configureRegistrationOpening(now.minusSeconds(60), Instant.parse("2030-01-01T00:00:00Z"));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> event.configureRegistrationOpening(now.plusSeconds(600), Instant.parse("2030-01-01T00:00:00Z"))).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(null, Instant.parse("2030-01-01T00:00:00Z")));
    }

    @Test
    void immediateOpeningIsFixedAfterPublication() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Location", now.plusSeconds(3600), now.plusSeconds(7200), 10);
        event.publish(0, now);
        event.configureRegistrationOpening(null, Instant.parse("2030-01-01T00:00:00Z"));
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(now.plusSeconds(600), Instant.parse("2030-01-01T00:00:00Z")));
    }

    @Test
    void registrationWindowRejectsEqualOrReversedBoundaries() {
        Instant start = Instant.parse("2035-01-01T00:00:00Z");
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Venue", start, start.plusSeconds(3600), 10);
        event.configureRegistrationDeadline(start.minusSeconds(60));
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(start.minusSeconds(60), Instant.parse("2030-01-01T00:00:00Z")));
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(start, Instant.parse("2030-01-01T00:00:00Z")));
        event.configureRegistrationOpening(start.minusSeconds(61), Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void cancelledEventCannotBeEditedOrPublishedAgain() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Venue", now.plusSeconds(3600), now.plusSeconds(7200), 10);
        assertThrows(ResponseStatusException.class, () -> event.cancel(1));
        event.publish(0, now);
        assertThrows(ResponseStatusException.class, () -> event.publish(0, now));
        event.cancel(0);
        assertThrows(ResponseStatusException.class, () -> event.cancel(0));
        assertThrows(ResponseStatusException.class, () -> event.editDetails("New", "Details", "Venue", now, now.plusSeconds(3600), 10, 0, now));
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationOpening(null, Instant.parse("2030-01-01T00:00:00Z")));
        assertThrows(ResponseStatusException.class, () -> event.configureRegistrationDeadline(now));
        assertEquals("CANCELLED", event.toView().status());
    }

    @Test
    void publishedScheduleLocksAtOriginalStartAndAllEditsLockAtOriginalEnd() {
        Instant start = Instant.parse("2030-01-01T10:00:00Z");
        Instant end = start.plusSeconds(3600);
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Venue", start, end, 10);
        event.publish(0, start.minusSeconds(60));
        // At the exact start, ordinary details remain editable but both time fields are fixed.
        event.editDetails("Title", "Details", "New venue", start, end, 10, 0, start);
        for (Instant changedStart : new Instant[] {start.minusSeconds(60), start.plusSeconds(60)}) {
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> event.editDetails(
                    "Title", "Details", "Venue", changedStart, end, 10, 0, start)).getStatusCode().value());
        }
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> event.editDetails(
                "Title", "Details", "Venue", start, end.plusSeconds(60), 10, 0, start)).getStatusCode().value());
        // The old end is decisive, even when the proposed schedule is in the future.
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> event.editDetails(
                "Reopened", "Details", "Venue", end.plusSeconds(60), end.plusSeconds(3600), 10, 0, end)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> event.editDetails(
                "Changed", "Details", "Venue", start, end, 10, 0, end)).getStatusCode().value());
        assertEquals("Title", event.toView().title());
        assertEquals("New venue", event.toView().location());
        assertEquals(start, event.toView().startsAt());
    }

    @Test
    void futurePublishedEventCanBeRescheduledButCannotBeMovedIntoThePast() {
        Instant now = Instant.parse("2030-01-01T09:00:00Z");
        Instant start = now.plusSeconds(3600);
        Event event = Event.draft(UUID.randomUUID(), "Title", "Details", "Venue", start, start.plusSeconds(3600), 10);
        event.publish(0, now);
        event.editDetails("Rescheduled", "Details", "Venue", start.plusSeconds(60), start.plusSeconds(7200), 10, 0, now);
        assertEquals(start.plusSeconds(60), event.toView().startsAt());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> event.editDetails(
                "Backdated", "Details", "Venue", now, start.plusSeconds(7200), 10, 0, now)).getStatusCode().value());
    }

}
