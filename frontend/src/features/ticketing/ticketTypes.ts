export const SGD_CURRENCY = "SGD";

// Sprint 3 uses a single currency and stores prices as minor currency units
// (SGD cents). Keep these fields isolated so the API contract can be adjusted
// during backend integration.
export interface TicketType {
  id: string;
  eventId?: string;
  name: string;
  priceMinor: number;
  currency: string;
  quota: number;
  bookedQuantity: number;
  version?: number;
}

export interface TicketTypePayload {
  name: string;
  priceMinor: number;
  currency: string;
  quota: number;
}

// Organizer-facing view: editing requires an optimistic-concurrency version
// supplied by the backend. It is never invented on the client.
export interface ManagedTicketType extends Omit<TicketType, "version"> {
  version: number;
}

export function isManagedTicketType(ticketType: TicketType): ticketType is ManagedTicketType {
  return typeof ticketType.version === "number";
}

export function publicTicketTypesPath(eventId: string) {
  return `/api/v1/events/${encodeURIComponent(eventId)}/ticket-types`;
}

export function organizerTicketTypesPath(eventId: string) {
  return `/api/v1/organizer/events/${encodeURIComponent(eventId)}/ticket-types`;
}

export function organizerTicketTypePath(eventId: string, ticketTypeId: string) {
  return `${organizerTicketTypesPath(eventId)}/${encodeURIComponent(ticketTypeId)}`;
}

// Event capacity is not ticket availability: remaining inventory is derived from
// the ticket type's own quota and booked quantity.
export function remainingFor(ticketType: TicketType): number {
  return Math.max(0, ticketType.quota - ticketType.bookedQuantity);
}

export function isSoldOut(ticketType: TicketType): boolean {
  return remainingFor(ticketType) === 0;
}

export function isFree(ticketType: TicketType): boolean {
  return ticketType.priceMinor === 0;
}

export function formatSgdPrice(priceMinor: number): string {
  return `S$${(priceMinor / 100).toFixed(2)}`;
}

// Converts a user-entered SGD dollar amount ("12.50") to minor units (1250).
// Returns null when the value is not a valid non-negative amount with at most
// two decimal places.
export function parseSgdPriceToMinor(value: string): number | null {
  const trimmed = value.trim();
  if (!/^\d+(\.\d{0,2})?$/.test(trimmed)) return null;
  const [whole, fraction = ""] = trimmed.split(".");
  const minor = Number(whole) * 100 + Number((fraction + "00").slice(0, 2));
  if (!Number.isSafeInteger(minor)) return null;
  return minor;
}

export function minorToSgdInput(priceMinor: number): string {
  return (priceMinor / 100).toFixed(2);
}
