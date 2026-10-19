package com.team10.sems.identity.internal.web;

import com.team10.sems.identity.UserView;
import com.team10.sems.identity.internal.application.UserAdministrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public UserAdministrationService.UserPage listUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "keyword", defaultValue = "") String keyword) {
        return administrationService.listUsers(page, size, keyword);
    }

    @GetMapping("/summary")
    public UserAdministrationService.UserSummary summary() { return administrationService.summary(); }

    @PutMapping("/{userId}/roles")
    public UserView replaceRoles(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody RoleUpdateRequest request) {
        return administrationService.replaceRoles(userId, request.roles());
    }
}
