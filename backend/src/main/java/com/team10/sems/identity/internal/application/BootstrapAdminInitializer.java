package com.team10.sems.identity.internal.application;

import com.team10.sems.identity.Role;
import com.team10.sems.identity.internal.domain.UserAccount;
import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import com.team10.sems.platform.config.BootstrapAdminProperties;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final BootstrapAdminProperties properties;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            UserAccountRepository users,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        String email = properties.email().strip().toLowerCase(Locale.ROOT);
        users.findByEmail(email).ifPresentOrElse(existing -> {
            if (!existing.getRoles().contains(Role.ADMIN)) {
                existing.addRole(Role.ADMIN);
            }
        }, () -> users.save(UserAccount.bootstrapAdmin(
                properties.username().strip().toLowerCase(Locale.ROOT),
                email,
                passwordEncoder.encode(properties.password()))));
    }
}
