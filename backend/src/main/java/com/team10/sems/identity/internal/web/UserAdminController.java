package com.team10.sems.identity.internal.web;

import com.team10.sems.identity.UserView;
import com.team10.sems.identity.internal.application.UserAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdministrationService administrationService;

    public UserAdminController(UserAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping
    public List<UserView> listUsers() {
        return administrationService.listUsers();
    }

    @PutMapping("/{userId}/roles")
    public UserView replaceRoles(
            @org.springframework.web.bind.annotation.PathVariable UUID userId,
            @Valid @RequestBody RoleUpdateRequest request) {
        return administrationService.replaceRoles(userId, request.roles());
    }
}
