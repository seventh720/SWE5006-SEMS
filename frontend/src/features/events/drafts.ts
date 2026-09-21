import type { PublishedEvent } from "./events";
import { ApiError } from "../../shared/api/client";

export interface ManagedEvent extends Omit<PublishedEvent, "status"> {
  status: "DRAFT" | "PUBLISHED" | "CANCELLED";
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ManagedPage {
  items: ManagedEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// datetime-local has no time zone. All event forms explicitly use Singapore time.
export function toSingaporeInput(instant: string) {
  return new Date(new Date(instant).getTime() + 8 * 60 * 60 * 1000).toISOString().slice(0, 19);
}

export function fromSingaporeInput(value: string) {
  return new Date(`${value}+08:00`).toISOString();
}

export function managementError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "An organizer or administrator role is required. Sign in again after your role is updated.";
    if (error.status === 404) return "This event is unavailable or does not belong to your account.";
    if (error.status === 409) return "This event has changed. Copy any unsaved text, then reload the latest version before editing.";
    if (error.status === 400) return error.message;
  }
  return "We couldn't complete the request. Please try again.";
}
