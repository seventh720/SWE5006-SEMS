package com.team10.sems;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.sems.event.internal.application.EventManagementService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
class BookingIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EventManagementService eventManagement;
    UUID owner, attendee, other, event, ticket;

    @BeforeEach
    void prepare() {
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

    private ResultActions book(UUID user, String key, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/bookings").with(as(user,"ATTENDEE"))
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("eventId",event,"ticketTypeId",ticket,"quantity",quantity))));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String confirmed(int quantity) throws Exception {
        return json(book(attendee,UUID.randomUUID().toString(),quantity).andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions cancel(String id, UUID user) throws Exception {
        return mvc.perform(post("/api/v1/bookings/" + id + "/cancel").with(as(user,"ATTENDEE")));
    }

    private ResultActions cancelEvent() throws Exception {
        return mvc.perform(post("/api/v1/organizer/events/" + event + "/cancel").with(as(owner,"ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"));
    }

    private int inventory() {
        return jdbc.queryForObject("SELECT booked_quantity FROM ticket_types WHERE id=?", Integer.class, ticket);
    }

    private int count(String status) {
        return jdbc.queryForObject("SELECT count(*) FROM bookings WHERE status=?",Integer.class,status);
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            var b = pool.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            return List.of(a.get(15,TimeUnit.SECONDS), b.get(15,TimeUnit.SECONDS));
        }
    }

    @Test
    void bookingSnapshotsReplayAndCancellationArePersistent() throws Exception {
        JsonNode created = json(book(attendee,"one",2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.totalAmountMinor").value(0)));
        String id = created.get("id").asText();
        book(attendee,"one",2).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        book(attendee,"one",3).andExpect(status().isConflict());
        assertEquals(2,inventory());
        mvc.perform(get("/api/v1/bookings").with(as(attendee,"ATTENDEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        cancel(id,attendee).andExpect(status().isOk()).andExpect(jsonPath("$.cancellationReason").value("ATTENDEE_CANCELLED"));
        cancel(id,attendee).andExpect(status().isOk());
        book(attendee,"one",2).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(0,inventory()); assertEquals(1,count("CANCELLED"));
    }

    @Test
    void eventCancellationReleasesAllOrdersAndKeepsHistory() throws Exception {
        String id = confirmed(2);
        book(other,"other",3).andExpect(status().isCreated());
        cancelEvent().andExpect(status().isOk());
        assertEquals(0,inventory()); assertEquals(0,count("CONFIRMED")); assertEquals(2,count("CANCELLED"));
        mvc.perform(get("/api/v1/events/" + event)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/bookings/" + id).with(as(attendee,"ATTENDEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eventTitle").value("Workshop"))
                .andExpect(jsonPath("$.cancellationReason").value("EVENT_CANCELLED"));
        mvc.perform(get("/api/v1/organizer/events/" + event + "/bookings").with(as(owner,"ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].attendeeId").exists()).andExpect(jsonPath("$.items[0].email").doesNotExist());
    }

    @Test
    void enforcesRolesOwnershipAndPagination() throws Exception {
        String id=confirmed(1);
        mvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized());
        for(String role:List.of("STAFF","ORGANIZER","ADMIN")) {
            mvc.perform(get("/api/v1/bookings").with(as(other,role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/bookings/"+id).with(as(other,"ATTENDEE"))).andExpect(status().isNotFound());
        cancel(id,other).andExpect(status().isNotFound());
        for(String role:List.of("ORGANIZER","ADMIN")) {
            mvc.perform(get("/api/v1/organizer/events/"+event+"/bookings").with(as(other,role))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/organizer/events/"+event+"/bookings").with(as(owner,"STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/bookings?page=-1").with(as(attendee,"ATTENDEE"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/bookings?size=51").with(as(attendee,"ATTENDEE"))).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsStartedPaidAndInvalidReservations() throws Exception {
        book(attendee,"zero",0).andExpect(status().isBadRequest());
        book(attendee,"large",11).andExpect(status().isBadRequest());
        book(attendee," ",1).andExpect(status().isBadRequest());
        jdbc.update("UPDATE ticket_types SET price_minor=100 WHERE id=?",ticket);
        book(attendee,"paid",1).andExpect(status().isConflict());
        jdbc.update("UPDATE ticket_types SET price_minor=0 WHERE id=?",ticket);
        jdbc.update("UPDATE events SET starts_at='2020-01-01T10:00:00Z',ends_at='2020-01-01T12:00:00Z' WHERE id=?",event);
        book(attendee,"past",1).andExpect(status().isConflict());
        assertEquals(0,inventory()); assertEquals(0,count("CONFIRMED"));
    }

    @Test
    void concurrentSameKeyProducesOneOrder() throws Exception {
        var results=race(() -> book(attendee,"same",1).andReturn().getResponse().getStatus(),
                () -> book(attendee,"same",1).andReturn().getResponse().getStatus());
        assertTrue(results.containsAll(List.of(200,201))); assertEquals(1,inventory()); assertEquals(1,count("CONFIRMED"));
    }

    @Test
    void concurrentLastTicketCannotOversell() throws Exception {
        jdbc.update("UPDATE ticket_types SET quota=1 WHERE id=?",ticket);
        var results=race(() -> book(attendee,"a",1).andReturn().getResponse().getStatus(),
                () -> book(other,"b",1).andReturn().getResponse().getStatus());
        assertTrue(results.containsAll(List.of(201,409))); assertEquals(1,inventory()); assertEquals(1,count("CONFIRMED"));
    }

    @Test
    void concurrentCancelOnlyReleasesOnce() throws Exception {
        String id=confirmed(3);
        assertEquals(List.of(200,200),race(() -> cancel(id,attendee).andReturn().getResponse().getStatus(),
                () -> cancel(id,attendee).andReturn().getResponse().getStatus()));
        assertEquals(0,inventory()); assertEquals(1,count("CANCELLED"));
    }

    @Test
    void eventCancellationSerializesWithNewBooking() throws Exception {
        var results=race(() -> book(attendee,"race",2).andReturn().getResponse().getStatus(),
                () -> cancelEvent().andReturn().getResponse().getStatus());
        assertEquals(200,results.get(1)); assertTrue(List.of(201,404).contains(results.get(0)));
        assertEquals(0,inventory()); assertEquals(0,count("CONFIRMED"));
    }

    @Test
    void eventAndAttendeeCancellationDoNotDoubleRelease() throws Exception {
        String id=confirmed(3);
        assertEquals(List.of(200,200),race(() -> cancel(id,attendee).andReturn().getResponse().getStatus(),
                () -> cancelEvent().andReturn().getResponse().getStatus()));
        assertEquals(0,inventory()); assertEquals(1,count("CANCELLED"));
    }

    @Test
    void inventoryRollsBackWhenOrderInsertFails() {
        // Unknown user passes a test JWT but fails the database FK after inventory is reserved.
        assertThrows(Exception.class,() -> book(UUID.randomUUID(),"fail",2).andReturn());
        assertEquals(0,inventory()); assertEquals(0,count("CONFIRMED"));
        assertEquals(false,jdbc.queryForObject("SELECT sales_started FROM ticket_types WHERE id=?",Boolean.class,ticket));
    }

    @Test
    void eventCancellationRollsBackWithOrdersAndInventory() throws Exception {
        confirmed(3);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(owner.toString(),"", "ROLE_ORGANIZER"));
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                eventManagement.cancel(owner,event,0);
                status.setRollbackOnly();
            });
        } finally { SecurityContextHolder.clearContext(); }
        assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM events WHERE id=?",String.class,event));
        assertEquals(3,inventory()); assertEquals(1,count("CONFIRMED"));
    }

    private ResultActions createType(int quota) throws Exception {
        return mvc.perform(post("/api/v1/organizer/events/"+event+"/ticket-types").with(as(owner,"ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Extra\",\"priceMinor\":0,\"currency\":\"SGD\",\"quota\":"+quota+"}"));
    }

    @Test
    void quotaAndDraftCapacityCannotExceedAllocation() throws Exception {
        createType(1).andExpect(status().isBadRequest());
        jdbc.update("UPDATE events SET status='DRAFT' WHERE id=?",event);
        mvc.perform(put("/api/v1/organizer/events/"+event).with(as(owner,"ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Workshop","description":"Description","location":"Singapore","startsAt":"2030-01-01T10:00:00Z",
                 "endsAt":"2030-01-01T12:00:00Z","capacity":9,"version":0}
                """)).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentQuotaCreationRespectsCapacity() throws Exception {
        jdbc.update("UPDATE ticket_types SET quota=9 WHERE id=?",ticket);
        var results=race(() -> createType(1).andReturn().getResponse().getStatus(),
                () -> createType(1).andReturn().getResponse().getStatus());
        assertTrue(results.containsAll(List.of(201,400)));
        assertEquals(10L,jdbc.queryForObject("SELECT sum(quota) FROM ticket_types WHERE event_id=?",Long.class,event));
    }

    @Test
    void publishedTicketIsEditableUntilFirstBookingEvenAfterCancellation() throws Exception {
        jdbc.update("UPDATE ticket_types SET quota=9 WHERE id=?",ticket);
        createType(1).andExpect(status().isCreated());
        String id=confirmed(1);
        cancel(id,attendee).andExpect(status().isOk());
        long version=jdbc.queryForObject("SELECT version FROM ticket_types WHERE id=?",Long.class,ticket);
        mvc.perform(put("/api/v1/organizer/events/"+event+"/ticket-types/"+ticket).with(as(owner,"ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\",\"priceMinor\":0,\"currency\":\"SGD\",\"quota\":9,\"version\":"+version+"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotCancelActiveOrderAfterStart() throws Exception {
        String id=confirmed(1);
        jdbc.update("UPDATE events SET starts_at='2020-01-01T10:00:00Z',ends_at='2020-01-01T12:00:00Z' WHERE id=?",event);
        cancel(id,attendee).andExpect(status().isConflict()); assertEquals(1,inventory());
    }
}
