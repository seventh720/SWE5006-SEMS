package com.team10.sems.identity.internal.web;

import com.team10.sems.identity.Role;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record RoleUpdateRequest(@NotEmpty Set<Role> roles) {
}
