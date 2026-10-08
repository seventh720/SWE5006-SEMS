package com.team10.sems.booking.internal.domain;

import com.team10.sems.booking.AttendeeInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pre_registrations")
public class PreRegistration {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;
    @Column(nullable = false)
    private int quantity;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attendee_info", nullable = false, columnDefinition = "jsonb")
    private AttendeeInfo attendeeInfo;
    @Column(name = "booking_id")
    private UUID bookingId;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PreRegistration() { }

    public static PreRegistration create(UUID user, UUID event) {
        PreRegistration result = new PreRegistration();
        result.id = UUID.randomUUID();
        result.userId = user;
        result.eventId = event;
        return result;
    }

    public void update(UUID ticket, int quantity, AttendeeInfo info) {
        ticketTypeId = ticket;
        this.quantity = quantity;
        attendeeInfo = info;
        updatedAt = Instant.now();
    }

    public void markBooked(UUID booking) {
        if (bookingId == null) {
            bookingId = booking;
            updatedAt = Instant.now();
        }
    }

    public UUID id() { return id; }
    public UUID eventId() { return eventId; }
    public UUID ticketTypeId() { return ticketTypeId; }
    public int quantity() { return quantity; }
    public AttendeeInfo attendeeInfo() { return attendeeInfo; }
    public UUID bookingId() { return bookingId; }
    public Instant updatedAt() { return updatedAt; }
}
