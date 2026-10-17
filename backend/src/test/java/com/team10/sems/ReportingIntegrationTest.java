package com.team10.sems;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
class ReportingIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired com.team10.sems.ticketing.TicketLifecycleService lifecycle;

    UUID owner, attendee, other, event, ticket;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM issued_tickets");
        jdbc.update("DELETE FROM pre_registrations");
        jdbc.update("DELETE FROM bookings");
        jdbc.update("DELETE FROM ticket_types");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        owner = user(); attendee = user(); other = user();
        event = UUID.randomUUID(); ticket = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events(id,organizer_id,title,description,location,starts_at,ends_at,capacity,status)
                VALUES (?,?,'Workshop','Description','Singapore','2030-01-01T10:00:00Z','2030-01-01T12:00:00Z',10,'PUBLISHED')
                """, event, owner);
        jdbc.update("INSERT INTO ticket_types(id,event_id,name,price_minor,currency,quota) VALUES (?,?,'General',0,'SGD',10)", ticket, event);
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES (?,?,?,'unused')", id, id.toString(), id + "@example.test");
        return id;
    }

    private RequestPostProcessor as(UUID user, String role) {
        return jwt().jwt(token -> token.subject(user.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions statistics(UUID user, String role) throws Exception {
        return mvc.perform(get("/api/v1/organizer/events/" + event + "/statistics").with(as(user, role)));
    }

    private UUID insertBooking(UUID user, int quantity, String status, String reason) {
        UUID id = UUID.randomUUID();
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO bookings(id,user_id,event_id,ticket_type_id,request_key,quantity,
                    unit_price_minor,total_amount_minor,currency,event_title,event_location,
                    event_starts_at,event_ends_at,ticket_type_name,status,payment_status,cancellation_reason,created_at,updated_at)
                VALUES (?,?,?,?,?,?,0,0,'SGD','Workshop','Singapore','2030-01-01T10:00:00Z','2030-01-01T12:00:00Z','General',?,'NOT_REQUIRED',?,?,?)
                """, id, user, event, ticket, UUID.randomUUID().toString(), quantity, status, reason, now, now);
        return id;
    }

    private UUID insertTicket(UUID bookingId, String status, boolean historicalCheckIn) {
        UUID id = UUID.randomUUID();
        int ordinal = Objects.requireNonNull(
                jdbc.queryForObject("SELECT COALESCE(MAX(ordinal),0)+1 FROM issued_tickets WHERE booking_id=?", Integer.class, bookingId));
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        java.sql.Timestamp checkedInAt = "CHECKED_IN".equals(status) || ("VOID".equals(status) && historicalCheckIn) ? now : null;
        java.sql.Timestamp voidedAt = "VOID".equals(status) ? now : null;
        jdbc.update("INSERT INTO issued_tickets(id,booking_id,event_id,ordinal,status,checked_in_at,voided_at) VALUES (?,?,?,?,?,?,?)",
                id, bookingId, event, ordinal, status, checkedInAt, voidedAt);
        return id;
    }

    private ResultActions book(UUID user, String key, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/bookings").with(as(user, "ATTENDEE"))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("eventId", event, "ticketTypeId", ticket, "quantity", quantity))));
    }

    private String confirmed(int quantity) throws Exception {
        return json(book(attendee, UUID.randomUUID().toString(), quantity).andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions cancel(UUID id, UUID user) throws Exception {
        return mvc.perform(post("/api/v1/bookings/" + id + "/cancel").with(as(user, "ATTENDEE")));
    }

    private ResultActions cancelEvent() throws Exception {
        return mvc.perform(post("/api/v1/organizer/events/" + event + "/cancel").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"));
    }

    private int inventory() {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT booked_quantity FROM ticket_types WHERE id=?", Integer.class, ticket));
    }

    @Test
    void emptyEventReturnsAllZeros() throws Exception {
        statistics(owner, "ORGANIZER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.toString()))
                .andExpect(jsonPath("$.eventStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.validReservedTickets").value(0))
                .andExpect(jsonPath("$.cancelledTickets").value(0))
                .andExpect(jsonPath("$.checkedInTickets").value(0));
    }

    @Test
    void sumsTicketQuantitiesAcrossBookings() throws Exception {
        insertBooking(attendee, 3, "CONFIRMED", null);
        insertBooking(attendee, 2, "CONFIRMED", null);
        insertBooking(other, 4, "CANCELLED", "ATTENDEE_CANCELLED");
        statistics(owner, "ORGANIZER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validReservedTickets").value(5))
                .andExpect(jsonPath("$.cancelledTickets").value(4))
                .andExpect(jsonPath("$.checkedInTickets").value(0));
    }

    @Test
    void countsCheckedInTicketsButNotValidOrVoid() throws Exception {
        UUID booking = insertBooking(attendee, 5, "CONFIRMED", null);
        insertTicket(booking, "CHECKED_IN", false);
        insertTicket(booking, "CHECKED_IN", false);
        insertTicket(booking, "VALID", false);
        insertTicket(booking, "VALID", false);
        insertTicket(booking, "VALID", false);
        UUID voided = insertBooking(other, 2, "CANCELLED", "ATTENDEE_CANCELLED");
        insertTicket(voided, "VOID", true);
        insertTicket(voided, "VOID", true);
        statistics(owner, "ORGANIZER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validReservedTickets").value(5))
                .andExpect(jsonPath("$.cancelledTickets").value(2))
                .andExpect(jsonPath("$.checkedInTickets").value(2));
    }

    @Test
    void cancelledEventRemainsReportableAndExcludesHistoricalCheckIns() throws Exception {
        UUID bookingId = UUID.fromString(confirmed(3));
        var issued = lifecycle.issue(event, bookingId);
        lifecycle.checkIn(event, bookingId, issued.get(0).id());
        statistics(owner, "ORGANIZER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validReservedTickets").value(3))
                .andExpect(jsonPath("$.checkedInTickets").value(1));
        cancelEvent().andExpect(status().isOk());
        statistics(owner, "ORGANIZER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.validReservedTickets").value(0))
                .andExpect(jsonPath("$.cancelledTickets").value(3))
                .andExpect(jsonPath("$.checkedInTickets").value(0));
    }

    @Test
    void nonOwnerReceivesNotFound() throws Exception {
        insertBooking(attendee, 2, "CONFIRMED", null);
        statistics(other, "ORGANIZER").andExpect(status().isNotFound());
    }

    @Test
    void insufficientRoleIsRejected() throws Exception {
        insertBooking(attendee, 2, "CONFIRMED", null);
        statistics(attendee, "ATTENDEE").andExpect(status().isForbidden());
        statistics(other, "STAFF").andExpect(status().isForbidden());
    }

    @Test
    void missingOrInvalidAuthenticationIsRejected() throws Exception {
        mvc.perform(get("/api/v1/organizer/events/" + event + "/statistics"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/organizer/events/" + event + "/statistics")
                .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statisticsDoesNotDisturbSummaryBookingOrInventory() throws Exception {
        String id = confirmed(2);
        mvc.perform(get("/api/v1/organizer/events/summary").with(as(owner, "ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(1))
                .andExpect(jsonPath("$.cancelled").value(0));
        statistics(owner, "ORGANIZER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validReservedTickets").value(2));
        assertEquals(2, inventory());
        mvc.perform(get("/api/v1/organizer/events/summary").with(as(owner, "ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(1));
        cancel(UUID.fromString(id), attendee).andExpect(status().isOk());
        assertEquals(0, inventory());
        statistics(owner, "ORGANIZER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validReservedTickets").value(0))
                .andExpect(jsonPath("$.cancelledTickets").value(2));
    }
}
