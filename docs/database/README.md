# SEMS database management

PostgreSQL is the system database. Flyway SQL files under `backend/src/main/resources/db/migration` are the source of truth for its schema. Navicat is a supported local client for viewing data and testing queries.

## Rules

1. Create a new timestamped Flyway migration for every schema change.
2. Do not edit a migration after it has been merged or executed in a shared environment.
3. Keep Hibernate `ddl-auto` set to `validate`.
4. Commit schema migrations, reference data, ERD sources and the data dictionary.
5. Do not commit database dumps, PostgreSQL data files, passwords or personal Navicat connection files.
6. Test migrations from an empty PostgreSQL database before merging.

## Sprint 1 tables

| Table | Purpose |
|---|---|
| `users` | Login identity, password hash, status and audit timestamps |
| `roles` | Four supported system roles |
| `user_roles` | Many-to-many assignment of users to roles |

## Sprint 2 events

Migration: `V202609211600__create_events.sql`. No sample events are inserted.

| Column | PostgreSQL type | Meaning / constraint |
|---|---|---|
| `id` | UUID | Primary key |
| `organizer_id` | UUID | Required reference to `users.id`; no cascading deletion |
| `title` | VARCHAR(200) | Required, nonblank |
| `description` | VARCHAR(10000) | Required, nonblank |
| `location` | VARCHAR(500) | Required, nonblank |
| `starts_at`, `ends_at` | TIMESTAMPTZ | Required; start strictly before end |
| `capacity` | INTEGER | Required positive event size; Sprint 3 uses it as the ceiling for total ticket quota |
| `status` | VARCHAR(20) | Required; DRAFT (default), PUBLISHED or CANCELLED |
| `version` | BIGINT | Required optimistic-lock version, default 0 |
| `created_at`, `updated_at` | TIMESTAMPTZ | Required; default current timestamp on insert |

Indexes: published events by `(starts_at, id)` (partial index); organizer events by `(organizer_id, created_at DESC, id DESC)`. Substring search currently scans matching published titles; it does not use a full-text index. Public reads always filter `PUBLISHED`. Draft creation and editing now set audit timestamps through JPA lifecycle callbacks. Edit requests compare the supplied version, and JPA optimistic locking prevents concurrent overwrites. Writes use the authenticated organizer ID, and only DRAFT records are editable. Publication requires a future start time. Allowed transitions are DRAFT → PUBLISHED, DRAFT → CANCELLED, and PUBLISHED → CANCELLED. State transitions check the supplied version and use the same optimistic lock as editing. Cancelled records are retained and cannot be restored.

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : assigned
    users ||--o{ events : organizes
    events {
        uuid id PK
        uuid organizer_id FK
        varchar title
        varchar description
        varchar location
        timestamptz starts_at
        timestamptz ends_at
        integer capacity
        varchar status
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }
```

`EventBrowseIntegrationTest` runs against PostgreSQL 17 and verifies both empty-schema migration and upgrading Sprint 1 with existing user and role data. Run with `RUN_CONTAINER_TESTS=true mvn test` from `backend/`.

## Sprint 3 ticket types and bookings

Migrations: `V202610060400__create_ticket_types.sql` (existing ticket types) and `V202610060500__create_bookings.sql` (booking workflow and first-sale marker). Applied migrations are unchanged. The latter backfills `sales_started=true` where inventory is already booked; no orders are fabricated for historical inventory counters.

### Ticket types

| Column | Type | Meaning / constraint |
|---|---|---|
| id / event_id | UUID | Primary key / event FK |
| name | VARCHAR(100) | Nonblank ticket name |
| price_minor | BIGINT | Nonnegative SGD cents |
| currency | VARCHAR(3) | SGD only |
| quota | INTEGER | Positive quota; event-wide sum validated under the event lock |
| booked_quantity | INTEGER | Between 0 and quota |
| sales_started | BOOLEAN | Permanent first-booking marker; remains true after inventory release |
| version | BIGINT | Optimistic ticket edit version |
| created_at / updated_at | TIMESTAMPTZ | Audit times |

### Bookings

| Column | Type | Meaning / constraint |
|---|---|---|
| id | UUID | Primary key |
| user_id / event_id / ticket_type_id | UUID | Required FKs; history cannot be deleted by deleting its parent |
| request_key | VARCHAR(100) | Nonblank; unique together with user_id |
| quantity | INTEGER | 1–10 tickets |
| unit_price_minor / total_amount_minor | BIGINT | Free bookings only in Sprint 3; total equals unit price × quantity |
| currency | VARCHAR(3) | SGD |
| event_title / event_location | VARCHAR(200) / VARCHAR(500) | Historical event snapshot |
| event_starts_at / event_ends_at | TIMESTAMPTZ | Historical event times |
| ticket_type_name | VARCHAR(100) | Historical ticket name |
| status | VARCHAR(20) | CONFIRMED or CANCELLED |
| payment_status | VARCHAR(20) | NOT_REQUIRED |
| cancellation_reason | VARCHAR(30), nullable | ATTENDEE_CANCELLED or EVENT_CANCELLED; required exactly when cancelled |
| created_at / updated_at | TIMESTAMPTZ | Audit times |

Indexes support `(user_id, created_at DESC, id DESC)` and `(event_id, created_at DESC, id DESC)`. The idempotency unique constraint is `(user_id, request_key)`. PostgreSQL transaction advisory locks serialize same-key requests before taking the event row lock. All supported inventory writers hold the event row lock; cancellation reads the booking after acquiring it. See the [transaction contract](../sprint-3-api.zh-CN.md).

```mermaid
erDiagram
    users ||--o{ events : organizes
    events ||--o{ ticket_types : offers
    users ||--o{ bookings : reserves
    events ||--o{ bookings : contains
    ticket_types ||--o{ bookings : selected
    ticket_types {
        uuid id PK
        uuid event_id FK
        int quota
        int booked_quantity
        boolean sales_started
        bigint version
    }
    bookings {
        uuid id PK
        uuid user_id FK
        uuid event_id FK
        uuid ticket_type_id FK
        varchar request_key
        int quantity
        varchar status
        varchar cancellation_reason
    }
```

## Booking information and saved profiles

Migrations: `V202610061200__add_booking_information.sql`, followed by `V202610070900__reusable_profiles_and_booking_fields.sql`. Previously applied migrations remain unchanged.

| Table / columns | Type | Meaning |
|---|---|---|
| events.require_real_name / require_email / require_phone / require_student_id / require_passport | BOOLEAN NOT NULL DEFAULT FALSE | Required fields per order; editable in draft only |
| events.custom_field_label | VARCHAR(100), nullable | Organizer's question; a non-null label requires an answer |
| bookings.attendee_real_name | VARCHAR(100), nullable | Order contact's real name |
| bookings.attendee_email | VARCHAR(255), nullable | Order contact email, independent of login email |
| bookings.attendee_phone | VARCHAR(30), nullable | Contact phone |
| bookings.attendee_student_id / attendee_passport_number | VARCHAR(100), nullable | Independent document numbers |
| bookings.custom_field_label | VARCHAR(100), nullable | Question snapshot |
| bookings.attendee_custom_answer | VARCHAR(500), nullable | Answer snapshot |
| users.profile_real_name / profile_student_id / profile_passport_number | VARCHAR(100), nullable | Optional saved booking defaults; excluded from UserView |
| users.profile_email | VARCHAR(255), nullable | Optional saved contact email |
| users.profile_phone | VARCHAR(30), nullable | Optional saved contact phone |

Existing orders retain snapshots independently of profile changes. Only the current user's `/profile` API exposes saved defaults; order details are exposed through the attendee's own orders and the owning organizer's order list. Profiles do not populate booking requests automatically on the server.

The earlier generic `require_document` and `attendee_document_type/number` columns are retained as legacy data. The new migration converts earlier requirements to a custom identity-document question and copies earlier type/number values into the historical custom answer, preserving their meaning. New application writes use the independent fields above.

Event copying creates a new draft and new ticket types in one transaction, with zero booked quantity and no first-sale marker. No orders or user information are copied.

```mermaid
erDiagram
    users ||--o{ bookings : owns
    events ||--o{ bookings : requires_information_for
    users {
        uuid id PK
        varchar profile_real_name
        varchar profile_email
        varchar profile_phone
        varchar profile_student_id
        varchar profile_passport_number
    }
    events {
        uuid id PK
        boolean require_real_name
        boolean require_email
        boolean require_phone
        boolean require_student_id
        boolean require_passport
        varchar custom_field_label
    }
    bookings {
        uuid id PK
        uuid event_id FK
        varchar attendee_real_name
        varchar attendee_email
        varchar attendee_phone
        varchar attendee_student_id
        varchar attendee_passport_number
        varchar custom_field_label
        varchar attendee_custom_answer
    }
```
