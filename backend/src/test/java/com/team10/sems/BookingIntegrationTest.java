package com.team10.sems;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.sems.event.internal.application.EventManagementService;
import java.util.List;
import java.util.Objects;
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
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT booked_quantity FROM ticket_types WHERE id=?", Integer.class, ticket),
                "Ticket inventory must not be null");
    }

    private int count(String status) {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT count(*) FROM bookings WHERE status=?", Integer.class, status),
                "Booking count must not be null");
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
        long version = Objects.requireNonNull(
                jdbc.queryForObject("SELECT version FROM ticket_types WHERE id=?", Long.class, ticket),
                "Ticket version must not be null");
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

    private ResultActions bookWithInfo(String key, java.util.Map<String, String> info) throws Exception {
        return mvc.perform(post("/api/v1/bookings").with(as(attendee, "ATTENDEE"))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of(
                        "eventId", event, "ticketTypeId", ticket, "quantity", 1, "attendeeInfo", info))));
    }

    @Test
    void requiredInformationIsValidatedStoredAndIncludedInIdempotency() throws Exception {
        jdbc.update("UPDATE events SET require_real_name=true,require_email=true,require_phone=true,require_student_id=true,require_passport=true,custom_field_label='Department' WHERE id=?", event);
        var info = new java.util.HashMap<>(java.util.Map.of("realName", "Alice Tan", "email", "contact@example.test",
                "phone", "+65 8123 4567", "studentId", "A1234567", "passportNumber", "P1234567", "customAnswer", "Computing"));
        book(attendee, "missing", 1).andExpect(status().isBadRequest());
        for (String field : List.copyOf(info.keySet())) {
            var missing = new java.util.HashMap<>(info);
            missing.put(field, "   ");
            bookWithInfo("missing-" + field, missing).andExpect(status().isBadRequest());
        }
        assertEquals(0, inventory());
        assertEquals(false, jdbc.queryForObject("SELECT sales_started FROM ticket_types WHERE id=?", Boolean.class, ticket));
        String id = json(bookWithInfo("contact", info).andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendeeInfo.realName").value("Alice Tan"))).get("id").asText();
        bookWithInfo("contact", info).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        info.put("realName", "Another Person");
        bookWithInfo("contact", info).andExpect(status().isConflict());
        assertEquals(1, inventory());
        mvc.perform(get("/api/v1/bookings/" + id).with(as(attendee, "ATTENDEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendeeInfo.email").value("contact@example.test"));
        mvc.perform(get("/api/v1/organizer/events/" + event + "/bookings").with(as(owner, "ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].attendeeInfo.studentId").value("A1234567"));
        mvc.perform(get("/api/v1/bookings/" + id).with(as(other, "ATTENDEE"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/organizer/events/" + event + "/bookings").with(as(other, "ORGANIZER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/events/" + event)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingRequirements.studentId").value(true))
                .andExpect(jsonPath("$.attendeeInfo").doesNotExist());
        cancel(id, attendee).andExpect(status().isOk())
                .andExpect(jsonPath("$.attendeeInfo.realName").value("Alice Tan"));
    }

    @Test
    void rejectsMalformedAndUnrequestedInformationWithoutReservingInventory() throws Exception {
        bookWithInfo("unrequested", java.util.Map.of("realName", "Alice")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE events SET require_email=true,require_phone=true,require_student_id=true,require_passport=true,custom_field_label='Department' WHERE id=?", event);
        var info = new java.util.HashMap<>(java.util.Map.of("email", "contact@example.test", "phone", "+65 8123 4567",
                "studentId", "A1234567", "passportNumber", "P123456", "customAnswer", "Computing"));
        for (var invalid : java.util.Map.of("email", "invalid", "phone", "letters", "studentId", "x".repeat(101),
                "passportNumber", "x".repeat(101), "customAnswer", "x".repeat(501)).entrySet()) {
            var values = new java.util.HashMap<>(info);
            values.put(invalid.getKey(), invalid.getValue());
            bookWithInfo("invalid-" + invalid.getKey(), values).andExpect(status().isBadRequest());
        }
        assertEquals(0, inventory());
        assertEquals(0, count("CONFIRMED"));
    }

    @Test
    void organizerConfiguresRequirementsInDraftAndPublicationFixesThem() throws Exception {
        var input = new java.util.HashMap<String, Object>(java.util.Map.of(
                "title", "Registration", "description", "Details", "location", "Singapore",
                "startsAt", "2030-01-01T10:00:00Z", "endsAt", "2030-01-01T12:00:00Z", "capacity", 10,
                "bookingRequirements", java.util.Map.of("realName", true, "email", true, "phone", false, "studentId", false, "passport", false)));
        JsonNode draft = json(mvc.perform(post("/api/v1/organizer/events").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.bookingRequirements.realName").value(true)));
        String path = "/api/v1/organizer/events/" + draft.get("id").asText();
        input.put("version", draft.get("version").asLong());
        input.put("bookingRequirements", java.util.Map.of("realName", false, "email", false, "phone", true, "studentId", true, "passport", true, "customFieldLabel", "Department"));
        mvc.perform(put(path).with(as(other, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isNotFound());
        JsonNode updated = json(mvc.perform(put(path).with(as(owner, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk()));
        mvc.perform(get(path).with(as(owner, "ORGANIZER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingRequirements.phone").value(true));
        mvc.perform(post(path + "/ticket-types/default-free").with(as(owner, "ORGANIZER"))).andExpect(status().isOk());
        JsonNode published = json(mvc.perform(post(path + "/publish").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("version", updated.get("version").asLong()))))
                .andExpect(status().isOk()));
        input.put("version", published.get("version").asLong());
        input.put("bookingRequirements", java.util.Map.of("realName", true));
        mvc.perform(put(path).with(as(owner, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/events/" + draft.get("id").asText())).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingRequirements.studentId").value(true));
    }


    @Test
    void cancelledEventCanBeCopiedWithFreshTicketInventoryAndNoOrders() throws Exception {
        jdbc.update("UPDATE events SET require_student_id=true,custom_field_label='Department' WHERE id=?", event);
        bookWithInfo("source", java.util.Map.of("studentId", "A123", "customAnswer", "Computing"))
                .andExpect(status().isCreated());
        cancelEvent().andExpect(status().isOk());
        JsonNode copied = json(mvc.perform(post("/api/v1/organizer/events/" + event + "/copy")
                .with(as(owner, "ORGANIZER"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Workshop"))
                .andExpect(jsonPath("$.bookingRequirements.studentId").value(true))
                .andExpect(jsonPath("$.bookingRequirements.customFieldLabel").value("Department")));
        String id = copied.get("id").asText();
        assertNotEquals(event.toString(), id);
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
        JsonNode types = json(mvc.perform(get("/api/v1/organizer/events/" + id + "/ticket-types").with(as(owner, "ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("General"))
                .andExpect(jsonPath("$[0].quota").value(10)).andExpect(jsonPath("$[0].bookedQuantity").value(0))
                .andExpect(jsonPath("$[0].salesStarted").value(false)));
        assertNotEquals(ticket.toString(), types.get(0).get("id").asText());
        mvc.perform(get("/api/v1/organizer/events/" + id + "/bookings").with(as(owner, "ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM events WHERE id=?", String.class, event));
        assertEquals(1, count("CANCELLED"));
        mvc.perform(post("/api/v1/organizer/events/" + id + "/publish").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void eventCopyRequiresOwnerAndOrganizerRoleAndWorksForDrafts() throws Exception {
        String path = "/api/v1/organizer/events/" + event + "/copy";
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(as(owner, "ATTENDEE"))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(as(other, "ORGANIZER"))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(as(other, "ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(as(owner, "ORGANIZER"))).andExpect(status().isCreated());
        jdbc.update("UPDATE events SET status='DRAFT' WHERE id=?", event);
        mvc.perform(post(path).with(as(owner, "ORGANIZER"))).andExpect(status().isCreated());
    }

    @Test
    void profileIsPrivateOptionalAndIndependentOfSubmittedOrders() throws Exception {
        String profile = "{\"realName\":\"Saved Name\",\"email\":\"saved@example.test\",\"studentId\":\"S123\",\"passportNumber\":\"P456\"}";
        mvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/profile").contentType(MediaType.APPLICATION_JSON).content(profile))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/profile").with(as(attendee, "ATTENDEE")).contentType(MediaType.APPLICATION_JSON).content(profile))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentId").value("S123"));
        mvc.perform(get("/api/v1/profile").with(as(attendee, "ATTENDEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.realName").value("Saved Name"));
        mvc.perform(get("/api/v1/profile").with(as(owner, "ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentId").isEmpty());
        mvc.perform(get("/api/v1/profile/" + attendee).with(as(owner, "ADMIN"))).andExpect(status().isNotFound());
        jdbc.update("UPDATE events SET require_real_name=true WHERE id=?", event);
        String id = json(bookWithInfo("override", java.util.Map.of("realName", "Different Name"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attendeeInfo.studentId").isEmpty()))
                .get("id").asText();
        mvc.perform(get("/api/v1/profile").with(as(attendee, "ATTENDEE")))
                .andExpect(jsonPath("$.realName").value("Saved Name"));
        mvc.perform(put("/api/v1/profile").with(as(attendee, "ATTENDEE")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.realName").isEmpty());
        mvc.perform(get("/api/v1/bookings/" + id).with(as(attendee, "ATTENDEE")))
                .andExpect(jsonPath("$.attendeeInfo.realName").value("Different Name"));
        mvc.perform(put("/api/v1/profile").with(as(attendee, "ATTENDEE")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void studentIdAndPassportAreIndependentAndCustomAnswerKeepsItsLabel() throws Exception {
        jdbc.update("UPDATE events SET require_student_id=true WHERE id=?", event);
        bookWithInfo("passport-not-student", java.util.Map.of("passportNumber", "P123"))
                .andExpect(status().isBadRequest());
        bookWithInfo("student", java.util.Map.of("studentId", "S123")).andExpect(status().isCreated());
        jdbc.update("UPDATE events SET require_student_id=false,require_passport=true,custom_field_label='Dietary requirements' WHERE id=?", event);
        bookWithInfo("missing-custom", java.util.Map.of("passportNumber", "P123")).andExpect(status().isBadRequest());
        String id = json(bookWithInfo("custom", java.util.Map.of("passportNumber", "P123", "customAnswer", "Vegetarian"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.customFieldLabel").value("Dietary requirements")))
                .get("id").asText();
        // Historical labels remain stable even if event data is changed outside supported APIs.
        jdbc.update("UPDATE events SET custom_field_label='Changed label' WHERE id=?", event);
        mvc.perform(get("/api/v1/bookings/" + id).with(as(attendee, "ATTENDEE")))
                .andExpect(jsonPath("$.customFieldLabel").value("Dietary requirements"))
                .andExpect(jsonPath("$.attendeeInfo.customAnswer").value("Vegetarian"));
    }

    @Test
    void upgradePreservesEarlierDocumentRequirementsAndOrderInformation() throws Exception {
        confirmed(1);
        jdbc.update("UPDATE events SET require_document=true WHERE id=?", event);
        jdbc.update("UPDATE bookings SET attendee_document_type='PASSPORT',attendee_document_number='P123' WHERE event_id=?", event);
        var source = java.util.Objects.requireNonNull(jdbc.getDataSource());
        org.flywaydb.core.Flyway.configure().dataSource(source).schemas("reuse_upgrade")
                .target("202610061200").load().migrate();
        for (String table : List.of("users", "events", "ticket_types", "bookings")) {
            String columns = String.join(",", jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema='reuse_upgrade' AND table_name=? ORDER BY ordinal_position",
                    String.class, table));
            jdbc.execute("INSERT INTO reuse_upgrade." + table + " (" + columns + ") SELECT " + columns + " FROM public." + table);
        }
        org.flywaydb.core.Flyway.configure().dataSource(source).schemas("reuse_upgrade").load().migrate();
        assertEquals("Identity document number (student ID, passport or other)", jdbc.queryForObject(
                "SELECT custom_field_label FROM reuse_upgrade.events WHERE id=?", String.class, event));
        assertEquals("PASSPORT: P123", jdbc.queryForObject(
                "SELECT attendee_custom_answer FROM reuse_upgrade.bookings WHERE event_id=?", String.class, event));
    }


    @Test
    void registrationDeadlineStopsNewBookingsButAllowsReplayAndCancellation() throws Exception {
        String id = json(book(attendee, "before-deadline", 1).andExpect(status().isCreated())).get("id").asText();
        jdbc.update("UPDATE events SET registration_closes_at='2020-01-01T00:00:00Z' WHERE id=?", event);
        book(attendee, "after-deadline", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Registration for this event has closed"));
        assertEquals(1, inventory());
        assertEquals(1, count("CONFIRMED"));
        book(attendee, "before-deadline", 1).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/v1/events/" + event)).andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationClosesAt").value("2020-01-01T00:00:00Z"));
        cancel(id, attendee).andExpect(status().isOk());
        assertEquals(0, inventory());
    }

    @Test
    void deadlineIsValidatedPersistedAndCopied() throws Exception {
        var input = new java.util.HashMap<String, Object>(java.util.Map.of(
                "title", "Deadline test", "description", "Details", "location", "Singapore",
                "startsAt", "2030-01-01T10:00:00Z", "endsAt", "2030-01-01T12:00:00Z", "capacity", 10,
                "registrationClosesAt", "2030-01-01T11:00:00Z"));
        mvc.perform(post("/api/v1/organizer/events").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
        input.put("registrationClosesAt", "2030-01-01T09:00:00Z");
        JsonNode created = json(mvc.perform(post("/api/v1/organizer/events").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registrationClosesAt").value("2030-01-01T09:00:00Z")));
        String path = "/api/v1/organizer/events/" + created.get("id").asText();
        mvc.perform(post(path + "/copy").with(as(owner, "ORGANIZER"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationClosesAt").value("2030-01-01T09:00:00Z"));
        input.put("version", 0);
        input.put("registrationClosesAt", "2020-01-01T09:00:00Z");
        mvc.perform(put(path).with(as(owner, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk());
        mvc.perform(post(path + "/publish").with(as(owner, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}")).andExpect(status().isBadRequest());
        input.remove("registrationClosesAt");
        input.remove("version");
        mvc.perform(post("/api/v1/organizer/events").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registrationClosesAt").value("2030-01-01T10:00:00Z"));
    }


    @Test
    void eventIllustrationIsValidatedPersistedAndCopied() throws Exception {
        var input = new java.util.HashMap<String, Object>();
        input.put("title", "Illustrated event"); input.put("description", "Details"); input.put("location", "NUS");
        input.put("startsAt", "2030-01-01T10:00:00Z"); input.put("endsAt", "2030-01-01T12:00:00Z");
        input.put("capacity", 10); input.put("illustration", "MUSIC");
        String id = json(mvc.perform(post("/api/v1/organizer/events").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.illustration").value("MUSIC"))).get("id").asText();
        mvc.perform(post("/api/v1/organizer/events/" + id + "/copy").with(as(owner, "ORGANIZER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.illustration").value("MUSIC"));
        mvc.perform(post("/api/v1/organizer/events/" + id + "/ticket-types/default-free").with(as(owner, "ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quota").value(10));
        mvc.perform(post("/api/v1/organizer/events/" + id + "/publish").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.illustration").value("MUSIC"));
        input.put("illustration", "UNKNOWN");
        mvc.perform(post("/api/v1/organizer/events").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
    }

    @Test
    void publishedChangesPreserveOrdersAndExposeCurrentArrangements() throws Exception {
        String orderId = json(book(attendee, "before-edit", 1).andExpect(status().isCreated())).get("id").asText();
        String path = "/api/v1/organizer/events/" + event;
        JsonNode current = json(mvc.perform(get(path).with(as(owner, "ORGANIZER"))).andExpect(status().isOk()));
        var input = new java.util.HashMap<String, Object>();
        input.put("title", "Updated event"); input.put("description", "Updated description"); input.put("location", "New venue");
        input.put("startsAt", "2030-02-01T10:00:00Z"); input.put("endsAt", "2030-02-01T12:00:00Z");
        input.put("capacity", current.get("capacity").asInt()); input.put("version", current.get("version").asLong());
        input.put("registrationOpensAt", "2030-01-31T10:00:00Z"); input.put("registrationClosesAt", "2030-02-01T09:00:00Z");
        mvc.perform(put(path).with(as(other, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isNotFound());
        mvc.perform(put(path).with(as(owner, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/events/" + event)).andExpect(status().isOk()).andExpect(jsonPath("$.location").value("New venue"));
        mvc.perform(get("/api/v1/bookings/" + orderId).with(as(attendee, "ATTENDEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentEvent.location").value("New venue"))
                .andExpect(jsonPath("$.currentEvent.startsAt").value("2030-02-01T10:00:00Z"));
        assertNotEquals("New venue", jdbc.queryForObject("SELECT event_location FROM bookings WHERE id=?", String.class, UUID.fromString(orderId)));
        assertEquals(1, jdbc.queryForObject("SELECT booked_quantity FROM ticket_types WHERE id=?", Integer.class, ticket));
        mvc.perform(put(path).with(as(owner, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isConflict());
        input.put("version", current.get("version").asLong() + 1); input.put("capacity", 1);
        mvc.perform(put(path).with(as(owner, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
    }

    private ResultActions preRegister(UUID user, int quantity, java.util.Map<String, String> info) throws Exception {
        return mvc.perform(put("/api/v1/pre-registrations/" + event).with(as(user, "ATTENDEE"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of(
                        "eventId", event, "ticketTypeId", ticket, "quantity", quantity, "attendeeInfo", info))));
    }

    private void scheduleRegistration() {
        jdbc.update("UPDATE events SET registration_opens_at='2030-01-01T09:00:00Z' WHERE id=?", event);
    }

    @Test
    void preRegistrationIsPrivateAndIdempotentAndDoesNotHoldInventory() throws Exception {
        scheduleRegistration();
        jdbc.update("UPDATE events SET require_real_name=true WHERE id=?", event);
        book(attendee, "early", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Registration for this event has not opened yet"));
        preRegister(attendee, 2, java.util.Map.of()).andExpect(status().isBadRequest());
        String id = json(preRegister(attendee, 2, java.util.Map.of("realName", "Private Name"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING"))).get("id").asText();
        preRegister(attendee, 3, java.util.Map.of("realName", "Updated Name"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/v1/pre-registrations").with(as(attendee, "ATTENDEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].attendeeInfo.realName").value("Updated Name"));
        mvc.perform(get("/api/v1/pre-registrations/" + event).with(as(other, "ATTENDEE")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/pre-registrations/" + event).with(as(owner, "ORGANIZER")))
                .andExpect(status().isForbidden());
        assertEquals(0, inventory());
        assertEquals(0, count("CONFIRMED"));
        assertEquals(false, jdbc.queryForObject("SELECT sales_started FROM ticket_types WHERE id=?", Boolean.class, ticket));
        mvc.perform(delete("/api/v1/pre-registrations/" + event).with(as(other, "ATTENDEE")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/pre-registrations/" + event).with(as(attendee, "ATTENDEE")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/pre-registrations").with(as(attendee, "ATTENDEE")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void preRegistrationCanBecomeAnOrderOnlyAfterOpening() throws Exception {
        scheduleRegistration();
        preRegister(attendee, 1, java.util.Map.of()).andExpect(status().isOk());
        jdbc.update("UPDATE events SET registration_opens_at='2020-01-01T09:00:00Z' WHERE id=?", event);
        mvc.perform(get("/api/v1/pre-registrations/" + event).with(as(attendee, "ATTENDEE")))
                .andExpect(jsonPath("$.status").value("OPEN"));
        preRegister(attendee, 1, java.util.Map.of()).andExpect(status().isConflict());
        String booking = json(book(attendee, "opened", 1).andExpect(status().isCreated())).get("id").asText();
        mvc.perform(get("/api/v1/pre-registrations/" + event).with(as(attendee, "ATTENDEE")))
                .andExpect(jsonPath("$.status").value("BOOKED")).andExpect(jsonPath("$.bookingId").value(booking));
        book(attendee, "opened", 1).andExpect(status().isOk());
        assertEquals(1, inventory());
        mvc.perform(delete("/api/v1/pre-registrations/" + event).with(as(attendee, "ATTENDEE")))
                .andExpect(status().isNoContent());
        assertEquals(1, inventory());
        assertEquals(1, count("CONFIRMED"));
    }

    @Test
    void competingPreRegistrationsDoNotPromiseTheLastSeat() throws Exception {
        scheduleRegistration();
        jdbc.update("UPDATE ticket_types SET quota=1 WHERE id=?", ticket);
        var sameUser = race(() -> preRegister(attendee, 1, java.util.Map.of()).andReturn().getResponse().getStatus(),
                () -> preRegister(attendee, 1, java.util.Map.of()).andReturn().getResponse().getStatus());
        assertEquals(List.of(200, 200), sameUser);
        preRegister(other, 1, java.util.Map.of()).andExpect(status().isOk());
        assertEquals(0, inventory());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM pre_registrations", Integer.class));
        jdbc.update("UPDATE events SET registration_opens_at='2020-01-01T09:00:00Z' WHERE id=?", event);
        var bookings = race(() -> book(attendee, "one", 1).andReturn().getResponse().getStatus(),
                () -> book(other, "two", 1).andReturn().getResponse().getStatus());
        assertTrue(bookings.containsAll(List.of(201, 409)));
        assertEquals(1, inventory());
        assertEquals(1, count("CONFIRMED"));
    }

    @Test
    void preRegistrationTracksClosedAndCancelledEvents() throws Exception {
        scheduleRegistration();
        preRegister(attendee, 1, java.util.Map.of()).andExpect(status().isOk());
        jdbc.update("UPDATE events SET registration_opens_at='2019-01-01T00:00:00Z',registration_closes_at='2020-01-01T00:00:00Z' WHERE id=?", event);
        mvc.perform(get("/api/v1/pre-registrations/" + event).with(as(attendee, "ATTENDEE")))
                .andExpect(jsonPath("$.status").value("CLOSED"));
        cancelEvent().andExpect(status().isOk());
        mvc.perform(get("/api/v1/pre-registrations/" + event).with(as(attendee, "ATTENDEE")))
                .andExpect(jsonPath("$.status").value("EVENT_CANCELLED"));
        preRegister(attendee, 1, java.util.Map.of()).andExpect(status().isNotFound());
        assertEquals(0, inventory());
    }

    @Test
    void registrationOpeningIsValidatedAndRetainedInCopies() throws Exception {
        var input = new java.util.HashMap<String, Object>(java.util.Map.of("title", "Scheduled", "description", "Details",
                "location", "Singapore", "startsAt", "2030-01-01T10:00:00Z", "endsAt", "2030-01-01T12:00:00Z",
                "registrationClosesAt", "2030-01-01T09:30:00Z", "registrationOpensAt", "2030-01-01T09:30:00Z", "capacity", 10));
        mvc.perform(post("/api/v1/organizer/events").with(as(owner, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
        input.put("registrationOpensAt", "2030-01-01T09:00:00Z");
        JsonNode created = json(mvc.perform(post("/api/v1/organizer/events").with(as(owner, "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registrationOpensAt").value("2030-01-01T09:00:00Z")));
        mvc.perform(post("/api/v1/organizer/events/" + created.get("id").asText() + "/copy").with(as(owner, "ORGANIZER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registrationOpensAt").value("2030-01-01T09:00:00Z"));
    }

    @Test
    void preRegistrationRejectsInvalidRequestsAndRequiresAuthentication() throws Exception {
        scheduleRegistration();
        mvc.perform(get("/api/v1/pre-registrations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/pre-registrations?size=51").with(as(attendee, "ATTENDEE")))
                .andExpect(status().isBadRequest());
        preRegister(attendee, 0, java.util.Map.of()).andExpect(status().isBadRequest());
        preRegister(attendee, 11, java.util.Map.of()).andExpect(status().isBadRequest());
        preRegister(attendee, 1, java.util.Map.of("email", "not-requested@example.test")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE ticket_types SET price_minor=500 WHERE id=?", ticket);
        preRegister(attendee, 1, java.util.Map.of()).andExpect(status().isConflict());
    }

}
