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
  return new Date(new Date(instant).getTime() + 8 * 60 * 60 * 1000).toISOString().slice(0, 16);
}

export function validSingaporeInput(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return false;
  const date = new Date(`${value}+08:00`);
  return Number.isFinite(date.getTime()) && toSingaporeInput(date.toISOString()) === value;
}

export function fromSingaporeInput(value: string) {
  if (!validSingaporeInput(value)) throw new Error("Use a valid date and time in YYYY-MM-DD HH:mm format.");
  return new Date(`${value}+08:00`).toISOString();
}

export function managementError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "An organizer or administrator role is required. Sign in again after your role is updated.";
    if (error.status === 404) return "This event is unavailable or does not belong to your account.";
    if (error.status === 409) return error.problem.detail ?? "The update could not be saved. Copy any unsaved text before reloading the latest version.";
    if (error.status === 400) return error.message;
  }
  return "We couldn't complete the request. Please try again.";
}
