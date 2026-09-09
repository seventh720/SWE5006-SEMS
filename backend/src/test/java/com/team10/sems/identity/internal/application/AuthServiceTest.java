package com.team10.sems.identity.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.sems.identity.Role;
import com.team10.sems.identity.UserView;
import com.team10.sems.identity.internal.domain.UserAccount;
import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, passwordEncoder, tokenService);
    }

    @Test
    void newRegistrationReceivesAttendeeRoleAndStoresAHash() {
        when(passwordEncoder.encode("Password123")).thenReturn("stored-hash");
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserView registered = authService.register("Alice", "Alice@Example.com", "Password123");

        assertThat(registered.username()).isEqualTo("alice");
        assertThat(registered.email()).isEqualTo("alice@example.com");
        assertThat(registered.roles()).containsExactly(Role.ATTENDEE);
        verify(passwordEncoder).encode("Password123");
    }

    @Test
    void duplicateEmailIsRejected() {
        when(users.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.register("alice", "alice@example.com", "Password123"))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("Email");
    }
}
