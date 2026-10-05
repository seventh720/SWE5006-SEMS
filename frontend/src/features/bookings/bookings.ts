import { ApiError } from "../../shared/api/client";

// The client only supplies the minimum the backend needs to identify the
// reservation target. User identity, price, totals and status are all derived
// by the backend.
export interface BookingRequest {
  eventId: string;
  ticketTypeId: string;
  quantity: number;
}

// Minimal view of a created booking. The UI only needs an identifier for the
// confirmation message; electronic tickets arrive in a later phase.
export interface Booking {
  id: string;
}

// One key per logical reservation attempt. Retries of the SAME attempt must
// reuse the key; a genuinely new attempt calls this again for a fresh key.
// Keys must come from a secure random source — a weak key would let duplicate
// reservations collide on the backend's idempotency check.
export function newIdempotencyKey(): string {
  const cryptoApi = globalThis.crypto;
  if (cryptoApi && typeof cryptoApi.randomUUID === "function") {
    return cryptoApi.randomUUID();
  }
  if (cryptoApi && typeof cryptoApi.getRandomValues === "function") {
    const bytes = new Uint8Array(16);
    cryptoApi.getRandomValues(bytes);
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  }
  throw new Error("A secure crypto source is required to generate a booking request key.");
}

export function validateBookingQuantity(raw: string, remaining: number): string | null {
  const value = raw.trim();
  if (!/^\d+$/.test(value)) return "Enter a whole number of tickets.";
  const quantity = Number(value);
  if (!Number.isSafeInteger(quantity) || quantity < 1) return "Enter a positive whole number of tickets.";
  if (quantity > remaining) return `Only ${remaining} of these tickets remain.`;
  if (quantity > 10) return "You can reserve up to 10 tickets per order.";
  return null;
}

export type ReservationErrorKind = "invalid" | "auth" | "forbidden" | "not-found" | "conflict" | "uncertain";

export interface ReservationFailure {
  kind: ReservationErrorKind;
  message: string;
  retryable: boolean;
}

// Classifies a failed reservation so the UI can show the right feedback and
// decide whether the same Idempotency-Key must be reused on retry.
export function classifyReservationError(error: unknown): ReservationFailure {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      const fieldErrors = error.problem.fieldErrors;
      const firstField = fieldErrors ? Object.values(fieldErrors)[0] : undefined;
      return { kind: "invalid", message: firstField ?? error.message, retryable: false };
    }
    if (error.status === 401) return { kind: "auth", message: "Your session has expired. Please sign in again.", retryable: false };
    if (error.status === 403) return { kind: "forbidden", message: "You are not permitted to book tickets.", retryable: false };
    if (error.status === 404) return { kind: "not-found", message: "This ticket type or event is no longer available.", retryable: false };
    if (error.status === 409) {
      const detail = error.problem.detail ?? error.problem.title;
      return { kind: "conflict", message: detail ?? "This ticket is no longer available to book.", retryable: false };
    }
    if (error.status >= 500) {
      return { kind: "uncertain", message: "We couldn't confirm your reservation. Please try again to avoid a duplicate booking.", retryable: true };
    }
    return { kind: "invalid", message: error.message, retryable: false };
  }
  return { kind: "uncertain", message: "We couldn't confirm your reservation. Please check your connection and try again.", retryable: true };
}
