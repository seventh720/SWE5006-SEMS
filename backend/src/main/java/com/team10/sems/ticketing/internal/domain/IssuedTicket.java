package com.team10.sems.ticketing.internal.domain;

import com.team10.sems.ticketing.TicketView;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        ticket.status = "VALID";
        return ticket;
    }

    public void checkIn() {
        if (!"VALID".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket has already been used or voided");
        }
        status = "CHECKED_IN";
        checkedInAt = Instant.now();
    }

    public void voidTicket() {
        if (!"VOID".equals(status)) {
            status = "VOID";
            voidedAt = Instant.now();
        }
    }

    public boolean hasBeenUsed() { return checkedInAt != null; }
    public TicketView toView() {
        return new TicketView(id, bookingId, eventId, ordinal, status, checkedInAt, voidedAt);
    }
}
