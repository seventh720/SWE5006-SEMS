package com.team10.sems.identity.internal.persistence;

import com.team10.sems.identity.Role;
import java.util.UUID;

/** A present account has a row even when its left-joined role is null. */
public record AccountRoleRow(UUID userId, Role role) {
}
