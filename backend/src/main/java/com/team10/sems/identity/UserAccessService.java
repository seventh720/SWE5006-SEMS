package com.team10.sems.identity;

import com.team10.sems.identity.internal.persistence.AccountRoleRow;
import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Current account permissions for authentication, without exposing persistence entities. */
@Service
public class UserAccessService {
    private final UserAccountRepository users;

    public UserAccessService(UserAccountRepository users) { this.users = users; }

    public Optional<Set<Role>> activeRoles(UUID userId) {
        // Account status and roles are read atomically by one scalar SELECT; no separate transaction is needed.
        var rows = users.findRolesByIdAndStatus(userId, UserStatus.ACTIVE);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.stream().map(AccountRoleRow::role).filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet()));
    }
}
