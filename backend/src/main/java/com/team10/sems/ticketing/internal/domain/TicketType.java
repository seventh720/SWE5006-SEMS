package com.team10.sems.ticketing.internal.domain;

import com.team10.sems.ticketing.TicketTypeView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_types")
public class TicketType {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int quota;

    @Column(name = "booked_quantity", nullable = false)
    private int bookedQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketType() {
        // Required by JPA.
    }

    private TicketType(
            UUID eventId,
            String name,
            long priceMinor,
            String currency,
            int quota) {

        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.name = requireName(name);
        this.priceMinor = requirePrice(priceMinor);
        this.currency = requireCurrency(currency);
        this.quota = requireQuota(quota);
        this.bookedQuantity = 0;
    }

    public static TicketType create(
            UUID eventId,
            String name,
            long priceMinor,
            String currency,
            int quota) {

        if (eventId == null) {
            throw new IllegalArgumentException("Event is required");
        }

        return new TicketType(
                eventId,
                name,
                priceMinor,
                currency,
                quota);
    }

    public void edit(
            String name,
            long priceMinor,
            String currency,
            int quota,
            long expectedVersion) {

        requireVersion(expectedVersion);

        if (quota < bookedQuantity) {
            throw new IllegalArgumentException(
                    "Quota cannot be less than booked quantity");
        }

        this.name = requireName(name);
        this.priceMinor = requirePrice(priceMinor);
        this.currency = requireCurrency(currency);
        this.quota = requireQuota(quota);
    }

    public void reserve(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException(
                    "Quantity must be at least 1");
        }

        if (quantity > 10) {
            throw new IllegalArgumentException(
                    "You can reserve up to 10 tickets per order");
        }

        if (priceMinor != 0) {
            throw new IllegalStateException(
                    "Paid ticket booking is not available yet");
        }

        if ((long) bookedQuantity + quantity > quota) {
            throw new IllegalStateException(
                    "Not enough tickets are available");
        }

        bookedQuantity += quantity;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion != version) {
            throw new IllegalStateException(
                    "Ticket type has been modified by another request");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Ticket type name is required");
        }

        String value = name.strip();

        if (value.length() > 100) {
            throw new IllegalArgumentException(
                    "Ticket type name must not exceed 100 characters");
        }

        return value;
    }

    private static long requirePrice(long priceMinor) {
        if (priceMinor < 0) {
            throw new IllegalArgumentException(
                    "Price must not be negative");
        }

        return priceMinor;
    }

    private static String requireCurrency(String currency) {
        if (!"SGD".equals(currency)) {
            throw new IllegalArgumentException(
                    "Only SGD is supported");
        }

        return currency;
    }

    private static int requireQuota(int quota) {
        if (quota <= 0) {
            throw new IllegalArgumentException(
                    "Quota must be greater than zero");
        }

        return quota;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public long getPriceMinor() {
        return priceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public int getQuota() {
        return quota;
    }

    public int getBookedQuantity() {
        return bookedQuantity;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public TicketTypeView toManagementView() {
        return new TicketTypeView(
                id,
                eventId,
                name,
                priceMinor,
                currency,
                quota,
                bookedQuantity,
                version);
    }

    public TicketTypeView toPublicView() {
        return new TicketTypeView(
                id,
                eventId,
                name,
                priceMinor,
                currency,
                quota,
                bookedQuantity,
                null);
    }
}