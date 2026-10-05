import { apiRequest } from "../../shared/api/client";
import {
  organizerTicketTypePath,
  organizerTicketTypesPath,
  publicTicketTypesPath,
  type TicketType,
  type TicketTypePayload,
} from "./ticketTypes";

// Isolated request helpers so the Sprint 3 API contract can be adjusted here
// without touching the UI components.
export function readPublicTicketTypes(eventId: string, signal?: AbortSignal): Promise<TicketType[]> {
  return apiRequest<TicketType[]>(publicTicketTypesPath(eventId), { signal });
}

export function readOrganizerTicketTypes(eventId: string, token: string, signal?: AbortSignal): Promise<TicketType[]> {
  return apiRequest<TicketType[]>(organizerTicketTypesPath(eventId), { signal }, token);
}

export function createTicketType(eventId: string, token: string, input: TicketTypePayload): Promise<TicketType> {
  return apiRequest<TicketType>(organizerTicketTypesPath(eventId), {
    method: "POST",
    body: JSON.stringify(input),
  }, token);
}

export function updateTicketType(
  eventId: string,
  ticketTypeId: string,
  token: string,
  input: TicketTypePayload & { version: number },
): Promise<TicketType> {
  return apiRequest<TicketType>(organizerTicketTypePath(eventId, ticketTypeId), {
    method: "PUT",
    body: JSON.stringify(input),
  }, token);
}
