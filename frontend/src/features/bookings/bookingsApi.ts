import { apiRequest } from "../../shared/api/client";
import {
  bookingPath,
  withCurrentEvent,
  bookingsPath,
  cancelBookingPath,
  type Booking,
  type BookingPage,
  type BookingRecord,
  type BookingRequest,
} from "./bookings";

export function createBooking(request: BookingRequest, token: string, idempotencyKey: string): Promise<Booking> {
  return apiRequest<Booking>("/api/v1/bookings", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(request),
  }, token);
}

export function readBookings(token: string, page: number, size: number, signal?: AbortSignal): Promise<BookingPage> {
  return apiRequest<BookingPage>(bookingsPath(page, size), { signal }, token)
    .then((data) => ({ ...data, items: data.items.map(withCurrentEvent) }));
}

export function readBooking(token: string, id: string, signal?: AbortSignal): Promise<BookingRecord> {
  return apiRequest<BookingRecord>(bookingPath(id), { signal }, token).then(withCurrentEvent);
}

export function cancelBooking(token: string, id: string): Promise<BookingRecord> {
  return apiRequest<BookingRecord>(cancelBookingPath(id), { method: "POST" }, token).then(withCurrentEvent);
}
