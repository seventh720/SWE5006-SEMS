package com.team10.sems.identity;

import java.util.Set;
import java.util.UUID;

public record UserRolesChanged(UUID userId, Set<Role> before, Set<Role> after, UUID operationId) {
    public UserRolesChanged {
        before = Set.copyOf(before);
        after = Set.copyOf(after);
    }
}
