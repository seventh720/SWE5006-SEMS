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
class StaffCheckInIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired com.team10.sems.ticketing.TicketLifecycleService lifecycle;

    @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;
    private static final Instant START = Instant.parse("2030-01-01T10:00:00Z");
    UUID owner, attendee, other, event, ticket;

    @BeforeEach
    void prepare() {
        org.mockito.Mockito.when(clock.instant()).thenReturn(START);
        jdbc.update("DELETE FROM ticket_check_in_records");
        jdbc.update("DELETE FROM event_staff_assignments");
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

    private UUID staff, issued;
    private void ready() throws Exception {
        staff = user();
        jdbc.update("INSERT INTO user_roles(user_id,role_code) VALUES (?,'STAFF')", staff);
        mvc.perform(put("/api/v1/staff/events/" + event + "/assignments/" + staff).with(as(owner, "ORGANIZER")))
                .andExpect(status().isOk());

        issued = insertTicket(insertBooking(attendee, 1, "CONFIRMED", null), "VALID", false);
    }
    private ResultActions submit(UUID actor, UUID eventId, String operation) throws Exception {
        return mvc.perform(post("/api/v1/staff/events/" + eventId + "/" + operation).with(as(actor, "STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"sems:ticket:" + issued + "\"}"));
    }
    @Test void verifiesWithoutMutationThenChecksInExactlyOnce() throws Exception {
        ready();
        submit(staff, event, "verify").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ticket_check_in_records", Integer.class));
        submit(staff, event, "check-ins").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHECKED_IN"));
        submit(staff, event, "check-ins").andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ticket_check_in_records WHERE operator_id=?", Integer.class, staff));
    }
    @Test void rejectsUnassignedAndRevokedStaff() throws Exception {
        ready();
        submit(other, event, "verify").andExpect(status().isForbidden());
        submit(other, event, "check-ins").andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/staff/events/" + event + "/assignments/" + staff).with(as(owner, "ORGANIZER")))
                .andExpect(status().isOk());
        submit(staff, event, "check-ins").andExpect(status().isForbidden());
    }
    @Test void rejectsVoidedAndWrongEventTickets() throws Exception {
        ready();
        UUID wrong = UUID.randomUUID();
        UUID originalEvent = event, originalType = ticket;
        event = UUID.randomUUID(); ticket = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events(id,organizer_id,title,description,location,starts_at,ends_at,capacity,status)
                VALUES (?,?,'Other','Description','Singapore','2030-01-01T10:00:00Z','2030-01-01T12:00:00Z',10,'PUBLISHED')
                """, event, owner);
        jdbc.update("INSERT INTO ticket_types(id,event_id,name,price_minor,currency,quota) VALUES (?,?,'General',0,'SGD',10)", ticket, event);
        wrong = insertTicket(insertBooking(attendee, 1, "CONFIRMED", null), "VALID", false);
        event = originalEvent; ticket = originalType;
        jdbc.update("UPDATE issued_tickets SET status='VOID',voided_at=CURRENT_TIMESTAMP WHERE id=?", issued);
        submit(staff, event, "check-ins").andExpect(status().isConflict());
        issued = wrong;
        submit(staff, event, "verify").andExpect(status().isNotFound());
        submit(staff, event, "check-ins").andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ticket_check_in_records", Integer.class));
    }
    @Test void enforcesOwnershipAndEventWindow() throws Exception {
        ready();
        mvc.perform(put("/api/v1/staff/events/" + event + "/assignments/" + other).with(as(other, "ORGANIZER")))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE events SET starts_at='2030-01-01T10:16:00Z' WHERE id=?", event);
        submit(staff, event, "check-ins").andExpect(status().isConflict());
        jdbc.update("UPDATE events SET status='CANCELLED' WHERE id=?", event);
        submit(staff, event, "verify").andExpect(status().isConflict());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"-901,409", "-900,200", "0,200", "899,200", "900,409", "901,409"})
    void enforcesExactFifteenMinuteBoundaries(long seconds, int expected) throws Exception {
        ready();
        org.mockito.Mockito.when(clock.instant()).thenReturn(START.plusSeconds(seconds));
        submit(staff, event, "verify").andExpect(status().is(expected));
        submit(staff, event, "check-ins").andExpect(status().is(expected));
    }
    @Test void closesAtEventEndWhenTheEventIsShorterThanFifteenMinutes() throws Exception {
        ready();
        jdbc.update("UPDATE events SET ends_at='2030-01-01T10:05:00Z' WHERE id=?", event);
        org.mockito.Mockito.when(clock.instant()).thenReturn(START.plusSeconds(300));
        submit(staff, event, "verify").andExpect(status().isConflict());
        submit(staff, event, "check-ins").andExpect(status().isConflict());
    }
}
