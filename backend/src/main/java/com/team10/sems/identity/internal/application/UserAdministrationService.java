package com.team10.sems.identity.internal.application;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.data.domain.PageRequest;
import com.team10.sems.identity.Role;
import com.team10.sems.identity.UserStatus;
import com.team10.sems.identity.UserView;
import com.team10.sems.identity.internal.domain.UserAccount;
import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAdministrationService {

    private final UserAccountRepository users;

    public UserAdministrationService(UserAccountRepository users) {
        this.users = users;
    }

    public record UserPage(List<UserView> items, int page, int size, long totalElements, int totalPages) { }
    public record UserSummary(long total, long organizers, long staff) { }

    @Transactional(readOnly = true)
    public UserPage listUsers(int page, int size, String keyword) {
        com.team10.sems.platform.query.QueryRange.page(page, size);
        var result = users.search(keyword.strip(), PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "createdAt", "id")));
        return new UserPage(result.map(UserAccount::toView).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public UserSummary summary() {
        return new UserSummary(users.count(), users.countByRole(Role.ORGANIZER), users.countByRole(Role.STAFF));
    }

    @Transactional
    public UserView replaceRoles(UUID userId, Set<Role> roles) {
        users.lockRoleAssignments();
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.isActive() && user.getRoles().contains(Role.ADMIN) && !roles.contains(Role.ADMIN)
                && users.countByStatusAndRole(UserStatus.ACTIVE, Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "At least one active administrator must remain. Assign another administrator first.");
        }
        user.replaceRoles(roles);
        return user.toView();
    }
}
