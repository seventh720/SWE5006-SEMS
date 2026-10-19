package com.team10.sems.ticketing.internal.domain;

import com.team10.sems.ticketing.TicketView;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issued_tickets")
public class IssuedTicket {
    @Id private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(nullable = false) private int ordinal;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "checked_in_at") private Instant checkedInAt;
    @Column(name = "voided_at") private Instant voidedAt;

    protected IssuedTicket() { }

    public static IssuedTicket issue(UUID bookingId, UUID eventId, int ordinal) {
        var ticket = new IssuedTicket();
        ticket.id = UUID.randomUUID();
        ticket.bookingId = bookingId;
        ticket.eventId = eventId;
        ticket.ordinal = ordinal;
        ticket.status = ValidTicketState.INSTANCE.code();
        return ticket;
    }

    public void checkIn(Instant now) {
        currentState().checkIn(this, now);
    }

    public void voidTicket(Instant now) {
        currentState().voidTicket(this, now);
    }

    private TicketState currentState() {
        // Resolve from the persisted discriminator, including after a fresh JPA load.
        return TicketState.fromCode(status);
    }

    void markCheckedIn(Instant now) {
        status = CheckedInTicketState.INSTANCE.code();
        checkedInAt = now;
    }

    void markVoided(Instant now) {
        status = VoidTicketState.INSTANCE.code();
        voidedAt = now;
    }

    public boolean hasBeenUsed() { return checkedInAt != null; }
    public TicketView toView() {
        return new TicketView(id, bookingId, eventId, ordinal, status, checkedInAt, voidedAt);
    }
}
