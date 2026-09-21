package com.team10.sems.event.internal.domain;

import com.team10.sems.event.EventView;
import com.team10.sems.event.EventManagementView;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {
    @Id
    private UUID id;
    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 10000)
    private String description;
    @Column(nullable = false, length = 500)
    private String location;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(nullable = false)
    private int capacity;
    @Column(nullable = false, length = 20)
    private String status;
    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() { }

    public static Event draft(UUID owner, String title, String description, String location,
            Instant startsAt, Instant endsAt, int capacity) {
        Event event = new Event();
        event.id = UUID.randomUUID();
        event.organizerId = owner;
        event.status = "DRAFT";
        event.assign(title, description, location, startsAt, endsAt, capacity);
        return event;
    }

    public void editDraft(String title, String description, String location,
            Instant startsAt, Instant endsAt, int capacity, long expectedVersion) {
        if (!"DRAFT".equals(status) || version != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This event has changed or is no longer a draft. Reload before editing.");
        }
        assign(title, description, location, startsAt, endsAt, capacity);
    }

    public void publish(long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        if (!"DRAFT".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft can be published");
        }
        if (!startsAt.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must be in the future before publishing");
        }
        status = "PUBLISHED";
    }

    public void cancel(long expectedVersion) {
        checkVersion(expectedVersion);
        if (!"DRAFT".equals(status) && !"PUBLISHED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event is already cancelled");
        }
        status = "CANCELLED";
    }

    private void checkVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This event has changed. Reload before continuing.");
        }
    }

    private void assign(String title, String description, String location,
            Instant startsAt, Instant endsAt, int capacity) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public EventManagementView toManagementView() {
        return new EventManagementView(id, title, description, location, startsAt, endsAt,
                capacity, status, version, createdAt, updatedAt);
    }

    public EventView toView() {
        return new EventView(id, title, description, location, startsAt, endsAt, capacity, status);
    }
}
