package com.team10.sems.identity.internal.web;

import com.team10.sems.identity.UserView;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserView user) {
}
