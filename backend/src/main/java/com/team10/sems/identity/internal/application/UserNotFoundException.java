package com.team10.sems.identity.internal.application;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("No user exists with id " + userId);
    }
}
