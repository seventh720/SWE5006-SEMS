package com.team10.sems.identity.internal.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.team10.sems.identity.Role;
import com.team10.sems.identity.UserStatus;
import com.team10.sems.identity.internal.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    @Query("""
            select new com.team10.sems.identity.internal.persistence.AccountRoleRow(u.id, r)
            from UserAccount u left join u.roles r
            where u.id = :userId and u.status = :status
            """)
    List<AccountRoleRow> findRolesByIdAndStatus(@Param("userId") UUID userId, @Param("status") UserStatus status);

    // Serialize role changes across requests and application instances using a stable row.
    @Query(value = "SELECT code FROM roles WHERE code = 'ADMIN' FOR NO KEY UPDATE", nativeQuery = true)
    String lockRoleAssignments();

    @Query("select count(u) from UserAccount u where u.status = :status and :role member of u.roles")
    long countByStatusAndRole(@Param("status") UserStatus status, @Param("role") Role role);

    @Query("select u from UserAccount u where locate(lower(:keyword), lower(u.username)) > 0 or locate(lower(:keyword), lower(u.email)) > 0")
    Page<UserAccount> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("select count(u) from UserAccount u where :role member of u.roles")
    long countByRole(@Param("role") Role role);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
