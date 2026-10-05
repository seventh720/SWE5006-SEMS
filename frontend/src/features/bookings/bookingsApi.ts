import { apiRequest } from "../../shared/api/client";
import type { Booking, BookingRequest } from "./bookings";

const BOOKINGS_PATH = "/api/v1/bookings";

export function createBooking(request: BookingRequest, token: string, idempotencyKey: string): Promise<Booking> {
  return apiRequest<Booking>(BOOKINGS_PATH, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(request),
  }, token);
}
