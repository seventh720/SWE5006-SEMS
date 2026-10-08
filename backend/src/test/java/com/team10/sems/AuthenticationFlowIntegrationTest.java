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

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
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
                .andExpect(jsonPath("$[0].roles[0]").value("ADMIN"));
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
}
