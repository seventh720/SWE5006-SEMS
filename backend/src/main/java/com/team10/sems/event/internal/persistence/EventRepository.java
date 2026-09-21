package com.team10.sems.event.internal.persistence;

import com.team10.sems.event.internal.domain.Event;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {
    // LOCATE treats % and _ as literal characters rather than SQL wildcards.
    @Query("""
            select e from Event e
            where e.status = 'PUBLISHED'
              and locate(lower(:keyword), lower(e.title)) > 0
            """)
    Page<Event> findPublished(@Param("keyword") String keyword, Pageable pageable);

    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    Optional<Event> findByIdAndOrganizerId(UUID id, UUID organizerId);

    Optional<Event> findByIdAndStatus(UUID id, String status);
}
