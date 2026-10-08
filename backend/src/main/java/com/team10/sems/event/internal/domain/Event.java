package com.team10.sems.event.internal.domain;

import com.team10.sems.event.EventAccessView;
import com.team10.sems.event.BookingRequirements;
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
    @Column(name = "registration_opens_at")
    private Instant registrationOpensAt;
    @Column(name = "illustration", nullable = false, length = 20)
    private String illustration = "GENERAL";
    @Column(name = "registration_closes_at")
    private Instant registrationClosesAt;
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

    @Column(name = "require_real_name", nullable = false)
    private boolean requireRealName;
    @Column(name = "require_email", nullable = false)
    private boolean requireEmail;
    @Column(name = "require_phone", nullable = false)
    private boolean requirePhone;
    @Column(name = "require_student_id", nullable = false)
    private boolean requireStudentId;
    @Column(name = "require_passport", nullable = false)
    private boolean requirePassport;
    @Column(name = "custom_field_label", length = 100)
    private String customFieldLabel;

    protected Event() { }

    public void configureBooking(BookingRequirements requirements) {
        BookingRequirements fields = requirements == null ? BookingRequirements.NONE : requirements;
        if (!"DRAFT".equals(status)) {
            if ("PUBLISHED".equals(status) && fields.equals(bookingRequirements())) return;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking requirements cannot change after publication");
        }
        requireRealName = fields.realName();
        requireEmail = fields.email();
        requirePhone = fields.phone();
        requireStudentId = fields.studentId();
        requirePassport = fields.passport();
        customFieldLabel = fields.customFieldLabel();
    }

    public void configureRegistrationDeadline(Instant deadline) {
        if ("CANCELLED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled events cannot be edited");
        }
        Instant effectiveDeadline = deadline == null ? startsAt : deadline;
        if (effectiveDeadline.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration deadline must be on or before start time");
        }
        registrationClosesAt = effectiveDeadline;
    }

    public void configureRegistrationOpening(Instant opening) {
        if ("CANCELLED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled events cannot be edited");
        }
        if (opening != null && !opening.isBefore(registrationDeadline())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration opening must be before the registration deadline");
        }
        registrationOpensAt = opening;
    }

    private Instant registrationDeadline() {
        return registrationClosesAt == null ? startsAt : registrationClosesAt;
    }

    private BookingRequirements bookingRequirements() {
        return new BookingRequirements(requireRealName, requireEmail, requirePhone, requireStudentId, requirePassport, customFieldLabel);
    }

    public static Event draft(UUID owner, String title, String description, String location,
            Instant startsAt, Instant endsAt, int capacity) {
        Event event = new Event();
        event.id = UUID.randomUUID();
        event.organizerId = owner;
        event.status = "DRAFT";
        event.assign(title, description, location, startsAt, endsAt, capacity);
        return event;
    }

    public void editDetails(String title, String description, String location,
            Instant startsAt, Instant endsAt, int capacity, long expectedVersion) {
        if ("CANCELLED".equals(status) || version != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This event has changed or is cancelled. Reload before editing.");
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
        if (!registrationDeadline().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration deadline must be in the future before publishing");
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

    public void configureIllustration(String selection) {
        if ("CANCELLED".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled events cannot be edited");
        illustration = selection == null ? "GENERAL" : selection;
        if (!java.util.Set.of("GENERAL", "TECH", "MUSIC", "SPORT", "ART", "SOCIAL").contains(illustration)) {
            throw new IllegalArgumentException("Unknown event illustration");
        }
    }

    public EventManagementView toManagementView() {
        return new EventManagementView(id, title, description, location, startsAt, endsAt,
                capacity, status, version, createdAt, updatedAt, bookingRequirements(), registrationDeadline(), registrationOpensAt, illustration);
    }

    public EventView toView() {
        return new EventView(id, title, description, location, startsAt, endsAt, capacity, status, bookingRequirements(), registrationDeadline(), registrationOpensAt, illustration);
    }
    public EventAccessView toAccessView() {
        return new EventAccessView(
                id,
                organizerId,
                startsAt,
                endsAt,
                capacity,
                status,
                title,
                location, bookingRequirements(), registrationDeadline(), registrationOpensAt);
    }
}
