package com.team10.sems;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
class EventBrowseIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    private UUID organizer;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        organizer = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, email, password_hash) VALUES (?, 'organizer', 'organizer@example.test', 'unused')", organizer);
    }

    private UUID event(String title, String status, String startsAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events (id, organizer_id, title, description, location, starts_at, ends_at, capacity, status)
                VALUES (?, ?, ?, 'Event description', 'Singapore', CAST(? AS TIMESTAMPTZ), '2031-01-01T12:00:00Z', 100, ?)
                """, id, organizer, title, startsAt, status);
        return id;
    }

    @Test
    void emptyDatabaseReturnsFrontendPageContract() throws Exception {
        mvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void anonymousListOnlyShowsPublishedEventsWithStablePagination() throws Exception {
        UUID first = event("First", "PUBLISHED", "2030-01-01T10:00:00Z");
        event("Second", "PUBLISHED", "2030-02-01T10:00:00Z");
        event("Secret draft", "DRAFT", "2030-01-01T09:00:00Z");
        event("Cancelled", "CANCELLED", "2030-01-01T09:00:00Z");
        mvc.perform(get("/api/v1/events").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first.toString()))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/events").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("Second"));
        mvc.perform(get("/api/v1/events").param("page", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void searchIsTrimmedCaseInsensitiveAndLiteral() throws Exception {
        event("Open DAY 100%_", "PUBLISHED", "2030-01-01T10:00:00Z");
        event("Other", "PUBLISHED", "2030-01-01T10:00:00Z");
        for (String keyword : new String[] {" open day ", "%_"}) {
            mvc.perform(get("/api/v1/events").param("keyword", keyword))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        }
        mvc.perform(get("/api/v1/events").param("keyword", "absent"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void detailUsesFrontendFieldsAndDoesNotExposeInternalData() throws Exception {
        UUID id = event("Open Day", "PUBLISHED", "2030-01-01T10:00:00Z");
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Open Day"))
                .andExpect(jsonPath("$.description").value("Event description"))
                .andExpect(jsonPath("$.location").value("Singapore"))
                .andExpect(jsonPath("$.startsAt").value("2030-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.endsAt").value("2031-01-01T12:00:00Z"))
                .andExpect(jsonPath("$.capacity").value(100))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.organizerId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void hiddenAndMissingDetailsHaveSameNotFoundResponse() throws Exception {
        UUID draft = event("Private title", "DRAFT", "2030-01-01T10:00:00Z");
        UUID cancelled = event("Cancelled title", "CANCELLED", "2030-01-01T10:00:00Z");
        for (UUID id : new UUID[] {draft, cancelled, UUID.randomUUID()}) {
            mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Event not found"))
                    .andExpect(jsonPath("$.title").value("Not Found"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=x", "page=1.5", "size=0", "size=51", "page=2147483647&size=50"})
    void invalidPaginationReturnsBadRequest(String query) throws Exception {
        mvc.perform(get("/api/v1/events?" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void onlyPublicGetsAreAnonymous() throws Exception {
        mvc.perform(post("/api/v1/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/organizer/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/events/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void databaseRejectsInvalidEventFields() {
        UUID id = event("Valid", "DRAFT", "2030-01-01T10:00:00Z");
        for (String assignment : new String[] {"capacity = 0", "status = 'UNKNOWN'", "ends_at = starts_at", "title = ' '", "location = ''", "description = ''"}) {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE events SET " + assignment + " WHERE id = ?", id));
        }
    }

    @Test
    void migrationUpgradesSprintOneWithoutLosingUsers() throws Exception {
        String schema = "upgrade_check";
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).target("202609090930").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("INSERT INTO upgrade_check.users (id, username, email, password_hash) VALUES ('00000000-0000-0000-0000-000000000001', 'existing', 'existing@example.test', 'unused')");
            statement.execute("INSERT INTO upgrade_check.user_roles (user_id, role_code) VALUES ('00000000-0000-0000-0000-000000000001', 'ATTENDEE')");
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(schema).load().migrate();
            try (var result = statement.executeQuery("SELECT count(*) FROM upgrade_check.users u JOIN upgrade_check.user_roles r ON r.user_id = u.id WHERE u.username = 'existing' AND r.role_code = 'ATTENDEE'")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            statement.executeQuery("SELECT * FROM upgrade_check.events").close();
        }
    }
    private RequestPostProcessor asUser(UUID id, String role) {
        return jwt().jwt(token -> token.subject(id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String draftBody(String title, Long version) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("title", title);
        body.put("description", "Details for attendees");
        body.put("location", "Room A");
        body.put("startsAt", "2030-01-01T10:00:00+08:00");
        body.put("endsAt", "2030-01-01T12:00:00+08:00");
        body.put("capacity", 120);
        if (version != null) body.put("version", version);
        return mapper.writeValueAsString(body);
    }

    private JsonNode createDraft() throws Exception {
        String response = mvc.perform(post("/api/v1/organizer/events")
                        .with(asUser(organizer, "ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(draftBody(" First draft ", null)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("First draft"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    @Test
    void ownerCanCreateReadAndEditPersistedPrivateDraft() throws Exception {
        JsonNode created = createDraft();
        String id = created.get("id").asText();
        assertEquals(organizer, jdbc.queryForObject("SELECT organizer_id FROM events WHERE id = ?", UUID.class, UUID.fromString(id)));
        mvc.perform(get("/api/v1/organizer/events").with(asUser(organizer, "ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(put("/api/v1/organizer/events/" + id).with(asUser(organizer, "ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(draftBody("Updated draft", 0L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value("Updated draft"));
        mvc.perform(get("/api/v1/organizer/events/" + id).with(asUser(organizer, "ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated draft"))
                .andExpect(jsonPath("$.startsAt").value("2030-01-01T02:00:00Z"));
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/events")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anotherOrganizerAndAdminCannotReadOrEditOwnersDraft() throws Exception {
        String id = createDraft().get("id").asText();
        for (String role : new String[] {"ORGANIZER", "ADMIN"}) {
            UUID other = UUID.randomUUID();
            mvc.perform(get("/api/v1/organizer/events").with(asUser(other, role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
            mvc.perform(get("/api/v1/organizer/events/" + id).with(asUser(other, role)))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/v1/organizer/events/" + id).with(asUser(other, role))
                            .contentType(MediaType.APPLICATION_JSON).content(draftBody("Stolen", 0L)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void attendeeStaffAndAnonymousCannotManageEvents() throws Exception {
        String id = createDraft().get("id").asText();
        for (String role : new String[] {"ATTENDEE", "STAFF"}) {
            mvc.perform(get("/api/v1/organizer/events").with(asUser(organizer, role)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/organizer/events/" + id).with(asUser(organizer, role)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/organizer/events").with(asUser(organizer, role))
                            .contentType(MediaType.APPLICATION_JSON).content(draftBody("Denied", null)))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/organizer/events/" + id).with(asUser(organizer, role))
                            .contentType(MediaType.APPLICATION_JSON).content(draftBody("Denied", 0L)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/organizer/events").contentType(MediaType.APPLICATION_JSON)
                .content(draftBody("Denied", null))).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreatesOnlyOwnDraftAndCannotSpoofOwner() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(draftBody("Admin draft", null));
        body.put("organizerId", UUID.randomUUID().toString());
        body.put("status", "PUBLISHED");
        String response = mvc.perform(post("/api/v1/organizer/events").with(asUser(organizer, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(response).get("id").asText());
        assertEquals(organizer, jdbc.queryForObject("SELECT organizer_id FROM events WHERE id = ?", UUID.class, id));
    }

    @Test
    void staleEditsAndNonDraftEditsAreRejected() throws Exception {
        String id = createDraft().get("id").asText();
        mvc.perform(put("/api/v1/organizer/events/" + id).with(asUser(organizer, "ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(draftBody("New version", 0L)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/organizer/events/" + id).with(asUser(organizer, "ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(draftBody("Stale overwrite", 0L)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        for (String state : new String[] {"PUBLISHED", "CANCELLED"}) {
            UUID other = event("Read only", state, "2030-01-01T10:00:00Z");
            mvc.perform(put("/api/v1/organizer/events/" + other).with(asUser(organizer, "ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON).content(draftBody("Changed", 0L)))
                    .andExpect(status().isConflict());
        }
        assertEquals("New version", jdbc.queryForObject("SELECT title FROM events WHERE id = ?", String.class, UUID.fromString(id)));
    }

    @Test
    void invalidDraftsAndMissingVersionAreBadRequests() throws Exception {
        for (String field : new String[] {"title", "description", "location", "startsAt", "endsAt", "capacity"}) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(draftBody("Valid", null));
            body.remove(field);
            mvc.perform(post("/api/v1/organizer/events").with(asUser(organizer, "ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors." + field).exists());
        }
        for (String patch : new String[] {"{\"capacity\":0}", "{\"title\":\"   \"}", "{\"endsAt\":\"2029-01-01T00:00:00Z\"}"}) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(draftBody("Valid", null));
            body.setAll((com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(patch));
            mvc.perform(post("/api/v1/organizer/events").with(asUser(organizer, "ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        var fractional = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(draftBody("Valid", null));
        fractional.put("capacity", 1.5);
        mvc.perform(post("/api/v1/organizer/events").with(asUser(organizer, "ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(fractional)))
                .andExpect(status().isBadRequest());
        var tooLong = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(draftBody("Valid", null));
        tooLong.put("title", "x".repeat(201));
        mvc.perform(post("/api/v1/organizer/events").with(asUser(organizer, "ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(tooLong)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists());
        String id = createDraft().get("id").asText();
        mvc.perform(put("/api/v1/organizer/events/" + id).with(asUser(organizer, "ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(draftBody("No version", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/organizer/events?size=51").with(asUser(organizer, "ORGANIZER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void simultaneousTransactionsCannotOverwriteEachOther() {
        UUID id = event("Before", "DRAFT", "2030-01-01T10:00:00Z");
        try (var first = entityManagerFactory.createEntityManager(); var second = entityManagerFactory.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var one = first.find(com.team10.sems.event.internal.domain.Event.class, id);
            var two = second.find(com.team10.sems.event.internal.domain.Event.class, id);
            var start = java.time.Instant.parse("2030-01-01T10:00:00Z");
            one.editDraft("Winner", "Description", "Location", start, start.plusSeconds(3600), 10, 0);
            two.editDraft("Loser", "Description", "Location", start, start.plusSeconds(3600), 10, 0);
            first.getTransaction().commit();
            assertThrows(jakarta.persistence.RollbackException.class, () -> second.getTransaction().commit());
        }
        assertEquals("Winner", jdbc.queryForObject("SELECT title FROM events WHERE id = ?", String.class, id));
    }

    private org.springframework.test.web.servlet.ResultActions transition(String id, String action, long version,
            UUID user, String role) throws Exception {
        return mvc.perform(post("/api/v1/organizer/events/" + id + "/" + action)
                .with(asUser(user, role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + "}"));
    }

    @Test
    void publishAndCancelCompletePublicVisibilityLifecycle() throws Exception {
        String id = createDraft().get("id").asText();
        transition(id, "publish", 0, organizer, "ORGANIZER")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/events")).andExpect(jsonPath("$.items[0].id").value(id));
        transition(id, "publish", 1, organizer, "ORGANIZER").andExpect(status().isConflict());
        transition(id, "cancel", 0, organizer, "ORGANIZER").andExpect(status().isConflict());
        transition(id, "cancel", 1, organizer, "ORGANIZER")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/events")).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/organizer/events/" + id).with(asUser(organizer, "ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        transition(id, "publish", 2, organizer, "ORGANIZER").andExpect(status().isConflict());
        transition(id, "cancel", 2, organizer, "ORGANIZER").andExpect(status().isConflict());
    }

    @Test
    void draftCanBeCancelledAndPastEventCannotBePublished() throws Exception {
        String id = createDraft().get("id").asText();
        transition(id, "cancel", 0, organizer, "ADMIN")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        UUID past = event("Past", "DRAFT", "2020-01-01T10:00:00Z");
        transition(past.toString(), "publish", 0, organizer, "ORGANIZER")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM events WHERE id = ?", String.class, past));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "cancel"})
    void transitionRequiresOwnerRoleAuthenticationAndVersion(String action) throws Exception {
        String id = createDraft().get("id").asText();
        for (String role : new String[] {"ORGANIZER", "ADMIN"}) {
            transition(id, action, 0, UUID.randomUUID(), role).andExpect(status().isNotFound());
        }
        for (String role : new String[] {"ATTENDEE", "STAFF"}) {
            transition(id, action, 0, organizer, role).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/organizer/events/" + id + "/" + action)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isUnauthorized());
        for (String body : new String[] {"{}", "{\"version\":-1}", "{\"version\":0.5}"}) {
            mvc.perform(post("/api/v1/organizer/events/" + id + "/" + action)
                    .with(asUser(organizer, "ORGANIZER")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        transition(id, action, 10, organizer, "ORGANIZER").andExpect(status().isConflict());
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM events WHERE id = ?", String.class, UUID.fromString(id)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "cancel"})
    void editCannotOverwriteConcurrentStateTransition(String action) {
        UUID id = event("Before", "DRAFT", "2030-01-01T10:00:00Z");
        try (var editing = entityManagerFactory.createEntityManager(); var changing = entityManagerFactory.createEntityManager()) {
            editing.getTransaction().begin(); changing.getTransaction().begin();
            var draft = editing.find(com.team10.sems.event.internal.domain.Event.class, id);
            var transition = changing.find(com.team10.sems.event.internal.domain.Event.class, id);
            var start = java.time.Instant.parse("2030-01-01T10:00:00Z");
            draft.editDraft("Stale edit", "Description", "Location", start, start.plusSeconds(3600), 10, 0);
            if (action.equals("publish")) transition.publish(0, java.time.Instant.now()); else transition.cancel(0);
            changing.getTransaction().commit();
            assertThrows(jakarta.persistence.RollbackException.class, () -> editing.getTransaction().commit());
        }
        assertEquals("Before", jdbc.queryForObject("SELECT title FROM events WHERE id = ?", String.class, id));
        assertEquals(action.equals("publish") ? "PUBLISHED" : "CANCELLED", jdbc.queryForObject("SELECT status FROM events WHERE id = ?", String.class, id));
    }

    @Test
    void dashboardSummaryCountsAllOwnedEventsAndOnlyUpcomingPublished() throws Exception {
        for (int n = 0; n < 12; n++) event("Draft " + n, "DRAFT", "2030-01-01T10:00:00Z");
        UUID upcoming = event("Upcoming", "PUBLISHED", "2030-01-01T10:00:00Z");
        event("Past", "PUBLISHED", "2020-01-01T10:00:00Z");
        event("Cancelled", "CANCELLED", "2030-01-01T10:00:00Z");
        mvc.perform(get("/api/v1/organizer/events/summary").with(asUser(organizer, "ORGANIZER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.drafts").value(12))
                .andExpect(jsonPath("$.published").value(2)).andExpect(jsonPath("$.cancelled").value(1))
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].id").value(upcoming.toString()));
        mvc.perform(get("/api/v1/organizer/events/summary").with(asUser(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.drafts").value(0))
                .andExpect(jsonPath("$.upcoming").isEmpty());
        mvc.perform(get("/api/v1/organizer/events/summary")).andExpect(status().isUnauthorized());
        for (String role : new String[] {"ATTENDEE", "STAFF"}) {
            mvc.perform(get("/api/v1/organizer/events/summary").with(asUser(organizer, role)))
                    .andExpect(status().isForbidden());
        }
    }

}
