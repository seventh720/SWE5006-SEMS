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
