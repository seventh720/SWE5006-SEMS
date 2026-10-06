import { ApiError } from "../../shared/api/client";
import { formatSgdPrice } from "../ticketing/ticketTypes";

// The client only supplies the minimum the backend needs to identify the
// reservation target. User identity, price, totals and status are all derived
// by the backend.
export interface BookingRequest {
  eventId: string;
  ticketTypeId: string;
  quantity: number;
}

export type BookingStatus = "CONFIRMED" | "CANCELLED";

// Minimal response from creating a reservation (Phase 2). The reservation
// confirmation only needs the new booking id; the richer order-management
// snapshot lives in BookingRecord below.
export interface Booking {
  id: string;
  status?: BookingStatus;
}

// A booking snapshot returned by GET /bookings and GET /bookings/{id}. These
// fields are required by the Sprint 3 order experience, so they are typed
// non-optional; a response missing any of them is a broken contract, not a
// value the client should paper over. Genuinely conditional fields stay
// optional. Field names are isolated here so backend integration stays
// adjustable in one place.
export interface BookingRecord {
  id: string;
  eventTitle: string;
  eventLocation: string;
  eventStartsAt: string;
  eventEndsAt: string;
  ticketTypeName: string;
  quantity: number;
  unitPriceMinor: number;
  totalAmountMinor: number;
  currency: string;
  status: BookingStatus;
  paymentStatus?: string;
  cancellationReason?: string;
  createdAt?: string;
}

export interface BookingPage {
  items: BookingRecord[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function bookingsPath(page: number, size: number): string {
  return `/api/v1/bookings?page=${page}&size=${size}`;
}

export function bookingPath(id: string): string {
  return `/api/v1/bookings/${encodeURIComponent(id)}`;
}

export function cancelBookingPath(id: string): string {
  return `${bookingPath(id)}/cancel`;
}

export function readBookingPage(params: URLSearchParams): number {
  const raw = params.get("page") ?? "0";
  const page = Number(raw);
  return /^\d+$/.test(raw) && Number.isSafeInteger(page) ? page : 0;
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

// Formats a booking snapshot amount. Totals are backend-computed snapshots, so
// this is display-only formatting — the client never computes a chargeable price.
export function formatBookingAmount(totalAmountMinor: number): string {
  if (totalAmountMinor === 0) return "Free";
  return formatSgdPrice(totalAmountMinor);
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

// Order-management load failures. Mirrors the management error copy but never
// reveals whether a booking belongs to another user.
export function bookingError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "An attendee role is required to view bookings.";
    if (error.status === 404) return "This booking is unavailable or does not belong to your account.";
    if (error.status === 409) return error.problem.detail ?? error.problem.title ?? "This booking has changed and can no longer be modified.";
    if (error.status === 400) return error.message;
  }
  return "We couldn't load bookings right now. Please try again.";
}

// Cancellation failures. A 409 is a business conflict; network/5xx is uncertain
// and must never be presented as a completed cancellation.
export function cancelBookingError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 409) return error.problem.detail ?? error.problem.title ?? "This booking can no longer be cancelled.";
    if (error.status >= 500) return "We couldn't confirm your cancellation. Please reload to check the latest status.";
    return error.problem.detail ?? error.message;
  }
  return "We couldn't confirm your cancellation. Please reload to check the latest status.";
}
