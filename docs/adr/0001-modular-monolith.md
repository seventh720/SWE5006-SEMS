# ADR 0001 Modular monolith

## Status

Accepted for Sprint 1.

## Decision

SEMS is implemented as a frontend and one Spring Boot backend connected to one PostgreSQL database. The backend is divided into business modules: identity, event, ticketing, booking, attendance, notification and reporting.

Modules may use another module only through an explicit public API or an application event. They must not access another module's repository or persistence entity directly.

## Rationale

The original 50 man-day planning assumption and the updated five-sprint schedule do not justify independent services, deployment pipelines or distributed transactions. A modular monolith provides clear object-oriented boundaries while keeping local development and integration manageable.

## Consequences

- One deployable backend and one database are used initially.
- Module boundaries must still be reviewed in pull requests.
- A module can be extracted into a service later only when an actual scaling or deployment need appears.

## Sprint 3 clarification (2026-10-06)

The project must finish after five sprints. Remaining person-days must be checked against actual team effort. Booking uses one database transaction across module public services. Event mutations, inventory and order cancellations serialize on the event row. Synchronous application events validate capacity changes and cancel bookings before the event transaction commits; any failure rolls back the entire operation. No message broker or distributed transaction is needed for this scope.
