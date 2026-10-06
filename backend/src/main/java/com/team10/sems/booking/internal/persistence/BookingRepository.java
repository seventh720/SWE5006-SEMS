package com.team10.sems.booking.internal.persistence;

import com.team10.sems.booking.internal.domain.Booking;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByUserIdAndRequestKey(UUID userId, String requestKey);
    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);
    Page<Booking> findByUserId(UUID userId, Pageable pageable);
    Page<Booking> findByEventId(UUID eventId, Pageable pageable);
    List<Booking> findByEventIdAndStatusOrderByTicketTypeIdAscIdAsc(UUID eventId, String status);

    // Read only the immutable event ID before locking, not a potentially stale managed Booking.
    @Query("select b.eventId from Booking b where b.id = :id and b.userId = :userId")
    Optional<UUID> findOwnedEventId(@Param("id") UUID id, @Param("userId") UUID userId);
}
