package com.team10.sems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team10.sems.ticketing.TicketReservationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfEnvironmentVariable(
        named = "RUN_CONTAINER_TESTS",
        matches = "true")
class TicketTypeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    TicketReservationService reservations;

    private UUID organizer;
    private UUID otherOrganizer;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM ticket_types");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");

        organizer = UUID.randomUUID();
        otherOrganizer = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO users
                    (id, username, email, password_hash)
                VALUES
                    (?, 'organizer', 'organizer@example.test', 'unused')
                """, organizer);

        jdbc.update("""
                INSERT INTO users
                    (id, username, email, password_hash)
                VALUES
                    (?, 'other', 'other@example.test', 'unused')
                """, otherOrganizer);
    }

    private RequestPostProcessor asUser(UUID id, String role) {
        return jwt()
                .jwt(token -> token.subject(id.toString()))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_" + role));
    }

    private UUID event(UUID owner, String status) {
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO events
                    (id, organizer_id, title, description, location,
                     starts_at, ends_at, capacity, status)
                VALUES
                    (?, ?, 'Ticket Event', 'Description', 'Singapore',
                     '2030-01-01T10:00:00Z',
                     '2030-01-01T12:00:00Z',
                     500, ?)
                """, id, owner, status);

        return id;
    }

    private String ticketBody(
            String name,
            long priceMinor,
            String currency,
            int quota,
            Long version) throws Exception {

        var body =
                new java.util.LinkedHashMap<String, Object>();

        body.put("name", name);
        body.put("priceMinor", priceMinor);
        body.put("currency", currency);
        body.put("quota", quota);

        if (version != null) {
            body.put("version", version);
        }

        return mapper.writeValueAsString(body);
    }

    private JsonNode createTicketType(UUID eventId)
            throws Exception {

        String response = mvc.perform(
                        post("/api/v1/organizer/events/"
                                + eventId
                                + "/ticket-types")
                                .with(asUser(
                                        organizer,
                                        "ORGANIZER"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(ticketBody(
                                        "General admission",
                                        0,
                                        "SGD",
                                        100,
                                        null)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.eventId")
                        .value(eventId.toString()))
                .andExpect(jsonPath("$.name")
                        .value("General admission"))
                .andExpect(jsonPath("$.priceMinor")
                        .value(0))
                .andExpect(jsonPath("$.currency")
                        .value("SGD"))
                .andExpect(jsonPath("$.quota")
                        .value(100))
                .andExpect(jsonPath("$.bookedQuantity")
                        .value(0))
                .andExpect(jsonPath("$.version")
                        .value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return mapper.readTree(response);
    }

    @Test
    void ownerCanCreateListAndUpdateTicketType()
            throws Exception {

        UUID eventId = event(organizer, "DRAFT");

        JsonNode created = createTicketType(eventId);
        String ticketTypeId = created.get("id").asText();

        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM ticket_types
                        WHERE event_id = ?
                        """,
                        Integer.class,
                        eventId));

        mvc.perform(
                        get("/api/v1/organizer/events/"
                                + eventId
                                + "/ticket-types")
                                .with(asUser(
                                        organizer,
                                        "ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id")
                        .value(ticketTypeId))
                .andExpect(jsonPath("$[0].version")
                        .value(0));

        mvc.perform(
                        put("/api/v1/organizer/events/"
                                + eventId
                                + "/ticket-types/"
                                + ticketTypeId)
                                .with(asUser(
                                        organizer,
                                        "ORGANIZER"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(ticketBody(
                                        "VIP",
                                        5000,
                                        "SGD",
                                        50,
                                        0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name")
                        .value("VIP"))
                .andExpect(jsonPath("$.priceMinor")
                        .value(5000))
                .andExpect(jsonPath("$.quota")
                        .value(50))
                .andExpect(jsonPath("$.version")
                        .value(1));
    }

    @Test
    void attendeeStaffAndAnonymousCannotManageTicketTypes()
            throws Exception {

        UUID eventId = event(organizer, "DRAFT");

        for (String role :
                new String[]{"ATTENDEE", "STAFF"}) {

            mvc.perform(
                            post("/api/v1/organizer/events/"
                                    + eventId
                                    + "/ticket-types")
                                    .with(asUser(
                                            organizer,
                                            role))
                                    .contentType(
                                            MediaType.APPLICATION_JSON)
                                    .content(ticketBody(
                                            "Denied",
                                            0,
                                            "SGD",
                                            10,
                                            null)))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(
                        post("/api/v1/organizer/events/"
                                + eventId
                                + "/ticket-types")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(ticketBody(
                                        "Denied",
                                        0,
                                        "SGD",
                                        10,
                                        null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherOrganizerAndAdminCannotManageOwnersTicketTypes()
            throws Exception {

        UUID eventId = event(organizer, "DRAFT");

        for (String role :
                new String[]{"ORGANIZER", "ADMIN"}) {

            mvc.perform(
                            get("/api/v1/organizer/events/"
                                    + eventId
                                    + "/ticket-types")
                                    .with(asUser(
                                            otherOrganizer,
                                            role)))
                    .andExpect(status().isNotFound());

            mvc.perform(
                            post("/api/v1/organizer/events/"
                                    + eventId
                                    + "/ticket-types")
                                    .with(asUser(
                                            otherOrganizer,
                                            role))
                                    .contentType(
                                            MediaType.APPLICATION_JSON)
                                    .content(ticketBody(
                                            "Stolen",
                                            0,
                                            "SGD",
                                            10,
                                            null)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void invalidPriceQuotaCurrencyAndNameAreBadRequests()
            throws Exception {

        UUID eventId = event(organizer, "DRAFT");

        String[] invalidBodies = {
                ticketBody(
                        "Invalid", -1,
                        "SGD", 10, null),

                ticketBody(
                        "Invalid", 0,
                        "SGD", 0, null),

                ticketBody(
                        "Invalid", 0,
                        "USD", 10, null),

                ticketBody(
                        "   ", 0,
                        "SGD", 10, null)
        };

        for (String body : invalidBodies) {
            mvc.perform(
                            post("/api/v1/organizer/events/"
                                    + eventId
                                    + "/ticket-types")
                                    .with(asUser(
                                            organizer,
                                            "ORGANIZER"))
                                    .contentType(
                                            MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void updateRequiresVersionAndRejectsStaleVersion()
            throws Exception {

        UUID eventId = event(organizer, "DRAFT");

        String ticketTypeId =
                createTicketType(eventId)
                        .get("id")
                        .asText();

        mvc.perform(
                        put("/api/v1/organizer/events/"
                                + eventId
                                + "/ticket-types/"
                                + ticketTypeId)
                                .with(asUser(
                                        organizer,
                                        "ORGANIZER"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(ticketBody(
                                        "No version",
                                        0,
                                        "SGD",
                                        100,
                                        null)))
                .andExpect(status().isBadRequest());

        mvc.perform(
                        put("/api/v1/organizer/events/"
                                + eventId
                                + "/ticket-types/"
                                + ticketTypeId)
                                .with(asUser(
                                        organizer,
                                        "ORGANIZER"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(ticketBody(
                                        "Updated",
                                        0,
                                        "SGD",
                                        100,
                                        0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version")
                        .value(1));

        mvc.perform(
                        put("/api/v1/organizer/events/"
                                + eventId
                                + "/ticket-types/"
                                + ticketTypeId)
                                .with(asUser(
                                        organizer,
                                        "ORGANIZER"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(ticketBody(
                                        "Stale",
                                        0,
                                        "SGD",
                                        100,
                                        0L)))
                .andExpect(status().isConflict());
    }

    @Test
    void ticketTypesCannotBeChangedForCancelledEvents()
            throws Exception {

        for (String status :
                new String[]{"CANCELLED"}) {

            UUID eventId = event(organizer, status);

            mvc.perform(
                            post("/api/v1/organizer/events/"
                                    + eventId
                                    + "/ticket-types")
                                    .with(asUser(
                                            organizer,
                                            "ORGANIZER"))
                                    .contentType(
                                            MediaType.APPLICATION_JSON)
                                    .content(ticketBody(
                                            "Read only",
                                            0,
                                            "SGD",
                                            10,
                                            null)))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void anonymousCanReadTicketTypesOfPublishedEvent()
            throws Exception {

        UUID eventId = event(organizer, "DRAFT");

        createTicketType(eventId);

        jdbc.update(
                "UPDATE events SET status = 'PUBLISHED' WHERE id = ?",
                eventId);

        mvc.perform(
                        get("/api/v1/events/"
                                + eventId
                                + "/ticket-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name")
                        .value("General admission"))
                .andExpect(jsonPath("$[0].priceMinor")
                        .value(0))
                .andExpect(jsonPath("$[0].quota")
                        .value(100))
                .andExpect(jsonPath("$[0].bookedQuantity")
                        .value(0))
                .andExpect(jsonPath("$[0].version")
                        .doesNotExist());
    }

    @Test
    void publicCannotReadDraftCancelledOrMissingEvent()
            throws Exception {

        UUID draft = event(organizer, "DRAFT");
        UUID cancelled =
                event(organizer, "CANCELLED");

        for (UUID eventId :
                new UUID[]{
                        draft,
                        cancelled,
                        UUID.randomUUID()
                }) {

            mvc.perform(
                            get("/api/v1/events/"
                                    + eventId
                                    + "/ticket-types"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void databaseRejectsInvalidTicketTypeFields() {

        UUID eventId = event(organizer, "DRAFT");
        UUID ticketTypeId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO ticket_types
                    (id, event_id, name, price_minor,
                     currency, quota, booked_quantity)
                VALUES
                    (?, ?, 'General', 0, 'SGD', 10, 0)
                """, ticketTypeId, eventId);

        for (String assignment :
                new String[]{
                        "price_minor = -1",
                        "quota = 0",
                        "currency = 'USD'",
                        "name = ' '",
                        "booked_quantity = -1",
                        "booked_quantity = 11"
                }) {

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbc.update(
                            "UPDATE ticket_types SET "
                                    + assignment
                                    + " WHERE id = ?",
                            ticketTypeId));
        }
    }

    @Test
    void reservationUpdatesBookedQuantity() throws Exception {
        UUID eventId = event(organizer, "DRAFT");

        UUID ticketTypeId = UUID.fromString(
                createTicketType(eventId)
                        .get("id")
                        .asText());

        jdbc.update(
                "UPDATE events SET status = 'PUBLISHED' WHERE id = ?",
                eventId);

        reservations.reserve(
                eventId,
                ticketTypeId,
                3);

        assertEquals(
                3,
                jdbc.queryForObject(
                        """
                        SELECT booked_quantity
                        FROM ticket_types
                        WHERE id = ?
                        """,
                        Integer.class,
                        ticketTypeId));
    }

    @Test
    void reservationCannotExceedRemainingQuota() throws Exception {
        UUID eventId = event(organizer, "DRAFT");

        UUID ticketTypeId = UUID.fromString(
                createTicketType(eventId)
                        .get("id")
                        .asText());

        jdbc.update(
                """
                UPDATE ticket_types
                SET booked_quantity = 95
                WHERE id = ?
                """,
                ticketTypeId);

        jdbc.update(
                "UPDATE events SET status = 'PUBLISHED' WHERE id = ?",
                eventId);

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> reservations.reserve(
                        eventId,
                        ticketTypeId,
                        6));

        assertEquals(
                95,
                jdbc.queryForObject(
                        """
                        SELECT booked_quantity
                        FROM ticket_types
                        WHERE id = ?
                        """,
                        Integer.class,
                        ticketTypeId));
    }

    @Test
    void reservationRejectsUnpublishedEvent() throws Exception {
        UUID eventId = event(organizer, "DRAFT");

        UUID ticketTypeId = UUID.fromString(
                createTicketType(eventId)
                        .get("id")
                        .asText());

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> reservations.reserve(
                        eventId,
                        ticketTypeId,
                        1));

        assertEquals(
                0,
                jdbc.queryForObject(
                        """
                        SELECT booked_quantity
                        FROM ticket_types
                        WHERE id = ?
                        """,
                        Integer.class,
                        ticketTypeId));
    }

    @Test
    void reservationRejectsTicketTypeFromDifferentEvent()
            throws Exception {

        UUID eventA = event(organizer, "DRAFT");

        UUID ticketTypeId = UUID.fromString(
                createTicketType(eventA)
                        .get("id")
                        .asText());

        UUID eventB = event(organizer, "PUBLISHED");

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> reservations.reserve(
                        eventB,
                        ticketTypeId,
                        1));

        assertEquals(
                0,
                jdbc.queryForObject(
                        """
                        SELECT booked_quantity
                        FROM ticket_types
                        WHERE id = ?
                        """,
                        Integer.class,
                        ticketTypeId));
    }
}