package com.team10.sems.booking.internal.persistence;

import com.team10.sems.booking.internal.domain.PreRegistration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreRegistrationRepository extends JpaRepository<PreRegistration, UUID> {
    Optional<PreRegistration> findByUserIdAndEventId(UUID userId, UUID eventId);
    Page<PreRegistration> findByUserId(UUID userId, Pageable pageable);
}
