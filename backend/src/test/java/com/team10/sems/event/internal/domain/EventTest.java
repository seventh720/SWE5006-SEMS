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
}
