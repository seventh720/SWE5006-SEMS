package com.team10.sems.event.internal.persistence;

import com.team10.sems.event.internal.domain.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {
    @Query("select e.id from Event e where e.status='PUBLISHED' and e.registrationOpensAt > :now and e.registrationOpensAt <= :until")
    List<UUID> findOpeningSoon(@Param("now") Instant now, @Param("until") Instant until);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> lockById(@Param("id") UUID id);

    long countByOrganizerIdAndStatus(UUID organizerId, String status);

    List<Event> findTop4ByOrganizerIdAndStatusAndStartsAtAfterOrderByStartsAtAscIdAsc(
            UUID organizerId, String status, Instant now);

    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    Optional<Event> findByIdAndOrganizerId(UUID id, UUID organizerId);

    Optional<Event> findByIdAndStatus(UUID id, String status);
}
