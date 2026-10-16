package com.team10.sems.identity.internal.application;

import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsernameService {
    private final UserAccountRepository users;
    public UsernameService(UserAccountRepository users) { this.users = users; }

    @Transactional
    public String update(UUID userId, String requested) {
        String username = requested.toLowerCase(Locale.ROOT);
        var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (username.equals(user.getUsername())) return username;
        if (users.existsByUsername(username)) throw new DuplicateUserException("Username is already registered");
        user.changeUsername(username);
        try {
            users.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "uk_users_username".equals(constraint.getConstraintName())) {
                    throw new DuplicateUserException("Username is already registered");
                }
            }
            throw exception;
        }
        return username;
    }
}
