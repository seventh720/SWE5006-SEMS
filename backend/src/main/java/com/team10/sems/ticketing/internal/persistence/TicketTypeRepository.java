package com.team10.sems.ticketing.internal.persistence;

import com.team10.sems.ticketing.internal.domain.TicketType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventIdOrderByCreatedAtAscIdAsc(UUID eventId);

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);
}