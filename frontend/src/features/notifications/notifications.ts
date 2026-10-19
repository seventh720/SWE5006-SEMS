import { apiRequest } from "../../shared/api/client";

export const notificationsEnabled = import.meta.env.VITE_NOTIFICATIONS_ENABLED !== "false";

export type NotificationType = "ROLES_CHANGED" | "STAFF_ASSIGNED" | "STAFF_REVOKED" | "BOOKING_CONFIRMED" |
  "BOOKING_CANCELLED" | "REGISTRATION_OPENING_SOON" | "EVENT_UPDATED" | "EVENT_CANCELLED";

export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  createdAt: string;
  readAt: string | null;
  eventId?: string;
  bookingId?: string;
  ticketTypeId?: string;
}

export interface NotificationPage {
  items: Notification[];
  totalPages: number;
  unreadCount: number;
}

export const notificationLabels: Record<NotificationType, string> = {
  ROLES_CHANGED: "Roles changed",
  STAFF_ASSIGNED: "Staff assignment", STAFF_REVOKED: "Assignment removed",
  BOOKING_CONFIRMED: "Booking confirmed", BOOKING_CANCELLED: "Booking cancelled",
  REGISTRATION_OPENING_SOON: "Registration opens soon", EVENT_UPDATED: "Event updated",
  EVENT_CANCELLED: "Event cancelled",
};

export function notificationDestination(item: Notification) {
  if (item.type === "STAFF_ASSIGNED") return { to: "/staff", label: "View staff workspace" };
  if (item.type === "STAFF_REVOKED") return null;
  if (item.bookingId) return { to: `/bookings/${encodeURIComponent(item.bookingId)}`, label: "View order" };
  if (item.type === "EVENT_CANCELLED") return null;
  if (item.type === "REGISTRATION_OPENING_SOON") return { to: "/pre-registrations", label: "View pre-registrations" };
  if (item.eventId) return { to: `/events/${encodeURIComponent(item.eventId)}`, label: "View event" };
  return null;
}

export function readNotifications(token: string, page: number, unread: boolean, signal?: AbortSignal) {
  return apiRequest<NotificationPage>(`/api/v1/notifications?page=${page}&size=10&unreadOnly=${unread}`, { signal }, token);
}

export function markNotificationRead(token: string, id: string) {
  return apiRequest<void>(`/api/v1/notifications/${encodeURIComponent(id)}/read`, { method: "POST" }, token);
}
