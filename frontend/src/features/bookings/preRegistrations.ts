import { apiRequest } from "../../shared/api/client";
import type { AttendeeInfo } from "./attendeeInfo";
import type { BookingRequest } from "./bookings";

export interface PreRegistration {
  id: string;
  eventId: string;
  eventTitle: string;
  ticketTypeId: string;
  ticketTypeName: string;
  quantity: number;
  attendeeInfo: AttendeeInfo;
  customFieldLabel?: string;
  registrationOpensAt?: string;
  registrationClosesAt: string;
  status: "WAITING" | "OPEN" | "CLOSED" | "EVENT_CANCELLED" | "BOOKED";
  bookingId?: string;
}
export interface PreRegistrationPage {
  items: PreRegistration[];
  totalElements: number;
  totalPages: number;
}
export function preRegistrationPath(eventId: string) {
  return `/api/v1/pre-registrations/${encodeURIComponent(eventId)}`;
}
export function savePreRegistration(input: BookingRequest, token: string) {
  return apiRequest<PreRegistration>(preRegistrationPath(input.eventId), { method: "PUT", body: JSON.stringify(input) }, token);
}
export function readPreRegistration(eventId: string, token: string, signal?: AbortSignal) {
  return apiRequest<PreRegistration>(preRegistrationPath(eventId), { signal }, token);
}
export function preRegistrationStatus(item: PreRegistration, now: number): PreRegistration["status"] {
  if (["BOOKED", "EVENT_CANCELLED", "CLOSED"].includes(item.status)) return item.status;
  if (Date.parse(item.registrationClosesAt) <= now) return "CLOSED";
  return item.registrationOpensAt && Date.parse(item.registrationOpensAt) > now ? "WAITING" : "OPEN";
}
