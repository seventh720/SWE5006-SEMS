package com.team10.sems;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.sems.identity.internal.domain.UserAccount;
import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
class AuthenticationFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.security.oauth2.jwt.JwtEncoder encoder;

    @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;

    @BeforeEach
    void clearUsers() {
        org.mockito.Mockito.when(clock.instant()).thenAnswer(invocation -> java.time.Instant.now());
        jdbc.update("DELETE FROM notifications");
        users.deleteAll();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asUser(java.util.UUID id) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(id.toString()));
    }

    @Test
    void usernameCanBeChangedWithoutChangingIdentityOrLogin() throws Exception {
        var user = users.saveAndFlush(UserAccount.register("original", "rename@example.com", passwordEncoder.encode("Password123")));
        String token = login("rename@example.com", "Password123");
        mvc.perform(put("/api/v1/profile/username").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"New.Name\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("new.name"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.username").value("new.name")).andExpect(jsonPath("$.email").value("rename@example.com"));
        mvc.perform(put("/api/v1/profile/username").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"NEW.NAME\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("new.name"));
        org.junit.jupiter.api.Assertions.assertNotNull(login("rename@example.com", "Password123"));
    }

    @Test
    void usernameUpdateRejectsInvalidDuplicateAndAnonymousRequests() throws Exception {
        var first = users.saveAndFlush(UserAccount.register("first", "first@example.com", "unused"));
        var second = users.saveAndFlush(UserAccount.register("taken", "second@example.com", "unused"));
        for (String invalid : java.util.List.of("ab", "", "name with spaces", "x".repeat(51), "名字")) {
            mvc.perform(put("/api/v1/profile/username").with(asUser(first.getId()))
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of("username", invalid))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/profile/username").with(asUser(first.getId())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/profile/username").with(asUser(first.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"TAKEN\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/profile/username").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"another\"}"))
                .andExpect(status().isUnauthorized());
        // An extra userId in the body must never select another account.
        mvc.perform(put("/api/v1/profile/username").with(asUser(first.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("username", "renamed", "userId", second.getId()))))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("taken", users.findById(second.getId()).orElseThrow().getUsername());
        org.junit.jupiter.api.Assertions.assertEquals("renamed", users.findById(first.getId()).orElseThrow().getUsername());
    }

    @Test
    void concurrentUsernameClaimsHaveOnlyOneWinner() throws Exception {
        var first = users.saveAndFlush(UserAccount.register("one", "one@example.com", "unused"));
        var second = users.saveAndFlush(UserAccount.register("two", "two@example.com", "unused"));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = java.util.List.of(first, second).stream().map(user -> pool.submit(() -> {
                start.await();
                return mvc.perform(put("/api/v1/profile/username").with(asUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"shared\"}"))
                        .andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            var results = java.util.List.of(futures.get(0).get(15, java.util.concurrent.TimeUnit.SECONDS),
                    futures.get(1).get(15, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertTrue(results.containsAll(java.util.List.of(200, 409)));
        }
    }

    @Test
    void attendeeCanLoginButCannotUseAdminApi() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("ATTENDEE"));

        String attendeeToken = login("alice@example.com", "Password123");
        mvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanListUsers() throws Exception {
        users.save(UserAccount.bootstrapAdmin(
                "admin", "admin@example.com", passwordEncoder.encode("Password123")));

        String adminToken = login("admin@example.com", "Password123");
        mvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roles[0]").value("ADMIN"));
    }

    @Test
    void currentUserReturnsAccountWithoutPasswordAndRequiresAuthentication() throws Exception {
        var user = users.save(UserAccount.register("alice", "alice@example.com", passwordEncoder.encode("Password123")));
        String token = login("alice@example.com", "Password123");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ATTENDEE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownAccountHaveSameFailure() throws Exception {
        users.save(UserAccount.register("alice", "alice@example.com", passwordEncoder.encode("Password123")));
        for (String email : new String[] {"alice@example.com", "missing@example.com"}) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "WrongPassword"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }
    }

    @Test
    void roleAssignmentRequiresAdminAndNewLoginUpdatesTokenPermissions() throws Exception {
        users.save(UserAccount.bootstrapAdmin("admin", "admin@example.com", passwordEncoder.encode("Password123")));
        var attendee = users.save(UserAccount.register("alice", "alice@example.com", passwordEncoder.encode("Password123")));
        String oldToken = login("alice@example.com", "Password123");
        String adminToken = login("admin@example.com", "Password123");
        String path = "/api/v1/admin/users/" + attendee.getId() + "/roles";
        mvc.perform(put(path).header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ORGANIZER\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ATTENDEE\",\"ORGANIZER\"]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/organizer/events").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isForbidden());
        String fresh = login("alice@example.com", "Password123");
        mvc.perform(get("/api/v1/organizer/events").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + fresh))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("ORGANIZER")));
        mvc.perform(put(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredMalformedAndWrongIssuerTokensAreRejected() throws Exception {
        var user = users.save(UserAccount.register("alice", "alice@example.com", passwordEncoder.encode("Password123")));
        var now = java.time.Instant.now();
        for (String token : new String[] {"not-a-token", signedToken(user.getId().toString(), "sems-test", now.minusSeconds(600)),
                signedToken(user.getId().toString(), "wrong-issuer", now.plusSeconds(300))}) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        String valid = login("alice@example.com", "Password123");
        String[] parts = valid.split("\\.");
        String badSignature = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + parts[0] + "." + parts[1] + "." + badSignature))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usernamesAreUniqueIgnoringCase() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"Alice\",\"email\":\"alice@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("alice"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ALICE\",\"email\":\"other@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Username is already registered"));
    }

    @Test
    void concurrentRegistrationsCannotClaimTheSameUsername() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (String email : java.util.List.of("first@example.test", "second@example.test")) {
                tasks.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "username", "sameuser", "email", email, "password", "Password123"))))
                            .andReturn().getResponse().getStatus();
                }));
            }
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var statuses = java.util.List.of(tasks.get(0).get(15, java.util.concurrent.TimeUnit.SECONDS),
                    tasks.get(1).get(15, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertTrue(statuses.containsAll(java.util.List.of(201, 409)));
            org.junit.jupiter.api.Assertions.assertEquals(1, users.count());
        }
    }

    @Test
    void avatarsArePrivatePersistentAndCanBeRemoved() throws Exception {
        users.saveAndFlush(UserAccount.register("avatar", "avatar@example.com", passwordEncoder.encode("Password123")));
        users.saveAndFlush(UserAccount.register("otheravatar", "otheravatar@example.com", passwordEncoder.encode("Password123")));
        String token = login("avatar@example.com", "Password123");
        String other = login("otheravatar@example.com", "Password123");
        var image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        String data = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        mvc.perform(get("/api/v1/profile/avatar")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/profile/avatar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of("dataUrl", data))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dataUrl").isNotEmpty());
        mvc.perform(get("/api/v1/profile/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dataUrl").isNotEmpty());
        mvc.perform(get("/api/v1/profile/avatar").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dataUrl").isEmpty());
        for (String invalid : java.util.List.of("data:image/svg+xml;base64,PHN2Zz4=", "data:image/png;base64,bm90YW5pbWFnZQ==")) {
            mvc.perform(put("/api/v1/profile/avatar").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of("dataUrl", invalid))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/profile/avatar").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"dataUrl\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dataUrl").isEmpty());
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void revokedAdminPermissionIsRemovedFromExistingTokenWhileRetainedRolesStillWork() throws Exception {
        users.saveAndFlush(UserAccount.bootstrapAdmin("keeper", "keeper@example.test", passwordEncoder.encode("Password123")));
        var target = UserAccount.bootstrapAdmin("revoked", "revoked@example.test", passwordEncoder.encode("Password123"));
        target.addRole(com.team10.sems.identity.Role.ATTENDEE);
        users.saveAndFlush(target);
        String oldToken = login("revoked@example.test", "Password123");
        String keeper = login("keeper@example.test", "Password123");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + oldToken)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/users/" + target.getId() + "/roles").header("Authorization", "Bearer " + keeper)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ATTENDEE\"]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + oldToken)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/users/" + target.getId() + "/roles").header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + oldToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("ATTENDEE"));
    }

    @Test
    void revokedOrganizerAndStaffRolesCannotBeUsedByExistingToken() throws Exception {
        users.saveAndFlush(UserAccount.bootstrapAdmin("keeper", "keeper@example.test", passwordEncoder.encode("Password123")));
        var target = UserAccount.register("worker", "worker@example.test", passwordEncoder.encode("Password123"));
        target.addRole(com.team10.sems.identity.Role.ORGANIZER);
        target.addRole(com.team10.sems.identity.Role.STAFF);
        users.saveAndFlush(target);
        String oldToken = login("worker@example.test", "Password123");
        for (String path : java.util.List.of("/api/v1/organizer/events", "/api/v1/staff/events")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + oldToken)).andExpect(status().isOk());
        }
        mvc.perform(put("/api/v1/admin/users/" + target.getId() + "/roles")
                .header("Authorization", "Bearer " + login("keeper@example.test", "Password123"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ATTENDEE\"]}"))
                .andExpect(status().isOk());
        for (String path : java.util.List.of("/api/v1/organizer/events", "/api/v1/staff/events")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + oldToken)).andExpect(status().isForbidden());
        }
    }

    @Test
    void unavailableAccountsAndInvalidSubjectsRejectOtherwiseValidTokens() throws Exception {
        var user = users.saveAndFlush(UserAccount.register("closed", "closed@example.test", passwordEncoder.encode("Password123")));
        String token = login("closed@example.test", "Password123");
        for (String state : java.util.List.of("LOCKED", "DISABLED")) {
            jdbc.update("UPDATE users SET status=? WHERE id=?", state, user.getId());
            mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        users.deleteById(user.getId());
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        String malformedSubject = signedToken("not-a-uuid", "sems-test", java.time.Instant.now().plusSeconds(300));
        mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + malformedSubject)).andExpect(status().isUnauthorized());
    }

    @Test
    void lastActiveAdminCannotBeRemovedEvenWhenDisabledAdminsExist() throws Exception {
        var admin = users.saveAndFlush(UserAccount.bootstrapAdmin("lastadmin", "last@example.test", passwordEncoder.encode("Password123")));
        var disabled = users.saveAndFlush(UserAccount.bootstrapAdmin("disabledadmin", "disabled@example.test", "unused"));
        jdbc.update("UPDATE users SET status='DISABLED' WHERE id=?", disabled.getId());
        String token = login("last@example.test", "Password123");
        String path = "/api/v1/admin/users/" + admin.getId() + "/roles";
        mvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ATTENDEE\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("At least one active administrator must remain. Assign another administrator first."));
        org.junit.jupiter.api.Assertions.assertTrue(users.findById(admin.getId()).orElseThrow().getRoles().contains(com.team10.sems.identity.Role.ADMIN));
        mvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\",\"ATTENDEE\"]}"))
                .andExpect(status().isOk());
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void concurrentSelfDemotionsLeaveOneActiveAdministrator() throws Exception {
        var first = users.saveAndFlush(UserAccount.bootstrapAdmin("firstadmin", "first@example.test", passwordEncoder.encode("Password123")));
        var second = users.saveAndFlush(UserAccount.bootstrapAdmin("secondadmin", "second@example.test", passwordEncoder.encode("Password123")));
        var tokens = java.util.Map.of(first.getId(), login("first@example.test", "Password123"),
                second.getId(), login("second@example.test", "Password123"));
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = tokens.entrySet().stream().map(entry -> pool.submit(() -> {
                ready.countDown();
                start.await();
                return mvc.perform(put("/api/v1/admin/users/" + entry.getKey() + "/roles")
                        .header("Authorization", "Bearer " + entry.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ATTENDEE\"]}"))
                        .andReturn().getResponse().getStatus();
            })).toList();
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var statuses = java.util.List.of(futures.get(0).get(15, java.util.concurrent.TimeUnit.SECONDS),
                    futures.get(1).get(15, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertTrue(statuses.containsAll(java.util.List.of(200, 409)), statuses.toString());
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.status='ACTIVE' AND r.role_code='ADMIN'", Integer.class));
    }

    private String signedToken(String subject, String issuer, java.time.Instant expiresAt) {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer(issuer).subject(subject)
                .issuedAt(expiresAt.minusSeconds(300)).expiresAt(expiresAt).claim("roles", java.util.List.of("ATTENDEE")).build();
        var header = org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        return encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }
    @Test
    void userSearchPaginatesAndSummaryCountsAllAccounts() throws Exception {
        users.saveAndFlush(UserAccount.bootstrapAdmin("admin", "admin@example.com", passwordEncoder.encode("Password123")));
        users.saveAndFlush(UserAccount.register("Alice", "alice@example.com", "unused"));
        users.saveAndFlush(UserAccount.register("Bob", "bob@example.com", "unused"));
        String token = login("admin@example.com", "Password123");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token).param("keyword", " EXAMPLE ").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token).param("keyword", "ALICE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token).param("keyword", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/admin/users/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token).param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordPolicyAllowsOnlyEightToSixteenAsciiLettersOrDigits() throws Exception {
        int index = 0;
        for (String password : java.util.List.of("12345678", "abcdefghijklmnop", "Password123")) {
            String username = "valid" + index++;
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("username", username,
                            "email", username + "@example.test", "password", password))))
                    .andExpect(status().isCreated());
        }
        for (String password : java.util.List.of("1234567", "a".repeat(17), "password!", "汉".repeat(25), "abcd efgh", "😀".repeat(8))) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("username", "invalid",
                            "email", "invalid@example.test", "password", password))))
                    .andExpect(status().isBadRequest());
        }
        org.junit.jupiter.api.Assertions.assertEquals(3, users.count());
        users.saveAndFlush(UserAccount.register("legacy", "legacy@example.test", passwordEncoder.encode("LegacyPasswordLong!123")));
        org.junit.jupiter.api.Assertions.assertNotNull(login("legacy@example.test", "LegacyPasswordLong!123"));
    }

    @Test
    void tokenExpiresAtOneHourAndJpaAuditingUsesTheSharedClock() throws Exception {
        var now = java.time.Instant.parse("2031-02-03T04:05:06Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        var user = users.saveAndFlush(UserAccount.register("clockuser", "clock@example.test", passwordEncoder.encode("Password123")));
        org.junit.jupiter.api.Assertions.assertEquals(now, user.toView().createdAt());
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"clock@example.test\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(3599));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(3600));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangesNotifyOnlyTheAffectedUserAndNoOpDoesNotDuplicate() throws Exception {
        var admin = users.saveAndFlush(UserAccount.bootstrapAdmin("notifier", "notifier@example.test", passwordEncoder.encode("Password123")));
        var target = users.saveAndFlush(UserAccount.register("recipient", "recipient@example.test", "unused"));
        String token = login("notifier@example.test", "Password123");
        String path = "/api/v1/admin/users/" + target.getId() + "/roles";
        var now = java.time.Instant.now().plusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(put(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"roles\":[\"ATTENDEE\",\"STAFF\"]}")).andExpect(status().isOk());
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id=? AND type='ROLES_CHANGED'", Integer.class, target.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id=?", Integer.class, admin.getId()));
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Objects.requireNonNull(jdbc.queryForObject(
                "SELECT message FROM notifications WHERE user_id=?", String.class, target.getId())).contains("Event Staff"));
        org.junit.jupiter.api.Assertions.assertEquals(now, java.util.Objects.requireNonNull(jdbc.queryForObject(
                "SELECT created_at FROM notifications WHERE user_id=?", java.sql.Timestamp.class, target.getId())).toInstant());
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT test_reject_role_notice CHECK (type <> 'ROLES_CHANGED') NOT VALID");
        try {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> mvc.perform(put(path)
                    .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"roles\":[\"ATTENDEE\"]}")));
            org.junit.jupiter.api.Assertions.assertTrue(users.findById(target.getId()).orElseThrow().getRoles().contains(com.team10.sems.identity.Role.STAFF));
        } finally { jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT test_reject_role_notice"); }
    }

}
