package com.team10.sems.booking.internal.domain;

import com.team10.sems.booking.BookingView;
import com.team10.sems.booking.OrganizerBookingView;
import com.team10.sems.event.EventAccessView;
import com.team10.sems.ticketing.TicketTypeView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;
    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;
    @Column(name = "total_amount_minor", nullable = false)
    private long totalAmountMinor;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "event_title", nullable = false, length = 200)
    private String eventTitle;
    @Column(name = "event_location", nullable = false, length = 500)
    private String eventLocation;
    @Column(name = "event_starts_at", nullable = false)
    private Instant eventStartsAt;
    @Column(name = "event_ends_at", nullable = false)
    private Instant eventEndsAt;
    @Column(name = "ticket_type_name", nullable = false, length = 100)
    private String ticketTypeName;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;
    @Column(name = "cancellation_reason", length = 30)
    private String cancellationReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() { }

    public static Booking confirmed(UUID user, String key, EventAccessView event,
            TicketTypeView ticket, int quantity) {
        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.userId = user;
        booking.eventId = event.id();
        booking.ticketTypeId = ticket.id();
        booking.requestKey = key;
        booking.quantity = quantity;
        booking.unitPriceMinor = ticket.priceMinor();
        booking.totalAmountMinor = Math.multiplyExact(ticket.priceMinor(), quantity);
        booking.currency = ticket.currency();
        booking.eventTitle = event.title();
        booking.eventLocation = event.location();
        booking.eventStartsAt = event.startsAt();
        booking.eventEndsAt = event.endsAt();
        booking.ticketTypeName = ticket.name();
        booking.status = "CONFIRMED";
        booking.paymentStatus = "NOT_REQUIRED";
        booking.createdAt = Instant.now();
        booking.updatedAt = booking.createdAt;
        return booking;
    }

    public boolean matches(UUID event, UUID ticket, int requestedQuantity) {
        return eventId.equals(event) && ticketTypeId.equals(ticket) && quantity == requestedQuantity;
    }

    public boolean cancel(String reason) {
        if ("CANCELLED".equals(status)) return false;
        status = "CANCELLED";
        cancellationReason = reason;
        updatedAt = Instant.now();
        return true;
    }

    public boolean isCancelled() { return "CANCELLED".equals(status); }
    public UUID eventId() { return eventId; }
    public UUID ticketTypeId() { return ticketTypeId; }
    public int quantity() { return quantity; }

    public BookingView toView() {
        return new BookingView(id, eventTitle, eventLocation, eventStartsAt, eventEndsAt,
                ticketTypeName, quantity, unitPriceMinor, totalAmountMinor, currency,
                status, paymentStatus, cancellationReason, createdAt);
    }

    public OrganizerBookingView toOrganizerView() {
        return new OrganizerBookingView(id, userId, ticketTypeName, quantity, status, cancellationReason, createdAt);
    }
}
