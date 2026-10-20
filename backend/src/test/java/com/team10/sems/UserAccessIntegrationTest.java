package com.team10.sems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team10.sems.identity.BookingProfile;
import com.team10.sems.identity.Role;
import com.team10.sems.identity.UserAccessService;
import com.team10.sems.identity.internal.domain.UserAccount;
import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.team10.sems.UserAccessIntegrationTest$SqlCapture"
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_CONTAINER_TESTS", matches = "true")
public class UserAccessIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private UserAccessService access;
    @Autowired private UserAccountRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void activeRolesUseOneNarrowQueryAndReturnAnImmutableSet() {
        var user = account();
        user.replaceRoles(Set.of(Role.ATTENDEE, Role.ORGANIZER, Role.STAFF));
        user.updateAvatar("data:image/png;base64," + "A".repeat(100 * 1024));
        user.updateBookingProfile(new BookingProfile("Test Person", "profile@example.com", "+6512345678", "student", "passport"));
        users.saveAndFlush(user);

        SqlCapture.start();
        Set<Role> roles;
        List<String> queries;
        try {
            roles = access.activeRoles(user.getId()).orElseThrow();
        } finally {
            queries = SqlCapture.finish();
        }

        assertThat(roles).containsExactlyInAnyOrder(Role.ATTENDEE, Role.ORGANIZER, Role.STAFF);
        assertThatThrownBy(() -> roles.add(Role.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst()).contains("user_roles", "status")
                .doesNotContain("avatar_data", "password_hash", "profile_", "username", "email", "created_at", "updated_at");
    }

    @Test
    void committedRoleChangesAreVisibleOnTheNextLookup() {
        var user = users.saveAndFlush(account());
        assertThat(access.activeRoles(user.getId())).contains(Set.of(Role.ATTENDEE));

        user.replaceRoles(Set.of(Role.ADMIN, Role.STAFF));
        user = users.saveAndFlush(user);
        assertThat(access.activeRoles(user.getId())).contains(Set.of(Role.ADMIN, Role.STAFF));

        user.replaceRoles(Set.of(Role.ATTENDEE));
        users.saveAndFlush(user);
        assertThat(access.activeRoles(user.getId())).contains(Set.of(Role.ATTENDEE));
    }

    @Test
    void unavailableAndDeletedAccountsAreAbsentEvenAfterAnActiveLookup() {
        var user = users.saveAndFlush(account());
        assertThat(access.activeRoles(user.getId())).contains(Set.of(Role.ATTENDEE));
        for (String status : List.of("LOCKED", "DISABLED")) {
            jdbc.update("UPDATE users SET status = ? WHERE id = ?", status, user.getId());
            assertThat(access.activeRoles(user.getId())).isEmpty();
        }
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", user.getId());
        assertThat(access.activeRoles(user.getId())).contains(Set.of(Role.ATTENDEE));
        users.deleteById(user.getId());
        assertThat(access.activeRoles(user.getId())).isEmpty();
        assertThat(access.activeRoles(UUID.randomUUID())).isEmpty();
    }

    @Test
    void activeAccountWithoutRoleRowsRemainsPresentWithNoAuthorities() {
        // Preserve the previous lookup's semantics even for a database row outside normal role assignment rules.
        var user = users.saveAndFlush(account());
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", user.getId());
        assertThat(access.activeRoles(user.getId())).contains(Set.of());
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", user.getId());
        assertThat(access.activeRoles(user.getId())).isEmpty();
    }

    @Test
    @EnabledIfSystemProperty(named = "sems.auth-lookup-benchmark", matches = "true")
    void compareLegacyAndProjectionLookups() {
        // Opt-in diagnostic only: real PostgreSQL, no timing assertions or HTTP capacity claims.
        var user = account();
        var userId = users.saveAndFlush(user).getId();
        var transaction = new TransactionTemplate(transactions);
        transaction.setReadOnly(true);
        Supplier<Optional<Set<Role>>> legacy = () -> transaction.execute(status -> users.findById(userId)
                .filter(UserAccount::isActive).map(found -> Set.copyOf(found.getRoles())));
        Supplier<Optional<Set<Role>>> projection = () -> access.activeRoles(userId);

        for (int avatarKiB : List.of(0, 100)) {
            byte[] avatar = new byte[avatarKiB * 1024 * 3 / 4];
            new java.util.Random(5006).nextBytes(avatar);
            String dataUrl = avatarKiB == 0 ? null : "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(avatar);
            jdbc.update("UPDATE users SET avatar_data = ? WHERE id = ?", dataUrl, userId);
            assertThat(projection.get()).isEqualTo(legacy.get());
            for (int i = 0; i < 200; i++) {
                legacy.get();
                projection.get();
            }
            var legacyTimes = new ArrayList<Long>();
            var projectionTimes = new ArrayList<Long>();
            for (int round = 0; round < 5; round++) {
                if (round % 2 == 0) {
                    measure(legacy, legacyTimes);
                    measure(projection, projectionTimes);
                } else {
                    measure(projection, projectionTimes);
                    measure(legacy, legacyTimes);
                }
            }
            report("legacy", avatarKiB, legacyTimes);
            report("projection", avatarKiB, projectionTimes);
        }
    }

    private static UserAccount account() {
        return UserAccount.register("lookup-user", "lookup@example.com", "unused-test-hash");
    }

    private static void measure(Supplier<Optional<Set<Role>>> lookup, List<Long> times) {
        for (int i = 0; i < 500; i++) {
            long start = System.nanoTime();
            var roles = lookup.get().orElseThrow();
            times.add(System.nanoTime() - start);
            assertThat(roles).containsExactly(Role.ATTENDEE);
        }
    }

    private static void report(String mode, int avatarKiB, List<Long> times) {
        long[] sorted = times.stream().mapToLong(Long::longValue).sorted().toArray();
        System.out.printf(java.util.Locale.ROOT,
                "AUTH_LOOKUP mode=%s avatarKiB=%d calls=%d meanMs=%.3f medianMs=%.3f p95Ms=%.3f%n",
                mode, avatarKiB, sorted.length, Arrays.stream(sorted).average().orElseThrow() / 1_000_000,
                sorted[sorted.length / 2] / 1_000_000.0, sorted[(int) Math.ceil(sorted.length * 0.95) - 1] / 1_000_000.0);
    }

    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> QUERIES = new ThreadLocal<>();

        static void start() { QUERIES.set(new ArrayList<>()); }

        static List<String> finish() {
            var queries = QUERIES.get();
            QUERIES.remove();
            return queries;
        }

        @Override
        public String inspect(String sql) {
            var queries = QUERIES.get();
            if (queries != null) {
                queries.add(sql);
            }
            return sql;
        }
    }
}
