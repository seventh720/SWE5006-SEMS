package com.team10.sems.ticketing.internal.persistence;

import com.team10.sems.ticketing.internal.domain.TicketType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    @Query("select coalesce(sum(t.quota), 0) from TicketType t where t.eventId = :eventId")
    long totalQuota(@Param("eventId") UUID eventId);

    List<TicketType> findByEventIdOrderByCreatedAtAscIdAsc(UUID eventId);

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);
}
