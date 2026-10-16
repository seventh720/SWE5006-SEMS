package com.team10.sems.ticketing.internal.persistence;

import com.team10.sems.ticketing.internal.domain.IssuedTicket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedTicketRepository extends JpaRepository<IssuedTicket, UUID> {
    List<IssuedTicket> findByBookingIdOrderByOrdinal(UUID bookingId);
    Optional<IssuedTicket> findByIdAndBookingIdAndEventId(UUID id, UUID bookingId, UUID eventId);
}
