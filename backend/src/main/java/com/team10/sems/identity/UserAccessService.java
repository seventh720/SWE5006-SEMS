package com.team10.sems.identity;

import com.team10.sems.identity.internal.domain.UserAccount;
import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Current account permissions for authentication, without exposing persistence entities. */
@Service
public class UserAccessService {
    private final UserAccountRepository users;

    public UserAccessService(UserAccountRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public Optional<Set<Role>> activeRoles(UUID userId) {
        return users.findById(userId).filter(UserAccount::isActive)
                .map(user -> Set.copyOf(user.getRoles()));
    }
}
