// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { NotificationsPage, NotificationEntry } from "./NotificationsPage";

type NotificationsModule = typeof import("./notifications");

const state = vi.hoisted(() => ({ enabled: true, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ token: "token", logout: state.logout }) }));
vi.mock("./notifications", async importOriginal => {
  const original = await importOriginal<NotificationsModule>();
  return { ...original, get notificationsEnabled() { return state.enabled; } };
});
const fetchMock = vi.fn<typeof fetch>();
const item = { id: "n1", type: "BOOKING_CONFIRMED", title: "Your booking is confirmed", message: "Workshop · 2 tickets",
  createdAt: "2026-10-16T08:00:00Z", readAt: null, bookingId: "b1" };
const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status });
beforeEach(() => { state.enabled = true; state.logout.mockClear(); fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it("does not query unfinished services or imply reminders are being sent", () => {
  state.enabled = false;
  render(<MemoryRouter><NotificationEntry /><NotificationsPage /></MemoryRouter>);
  expect(screen.getByText(/Registration reminders are not being sent/)).toBeTruthy();
  expect(screen.getByRole("link", { name: "Notifications" })).toBeTruthy();
  expect(fetchMock).not.toHaveBeenCalled();
});

it("marks a notification read on the server and refreshes the unread count", async () => {
  fetchMock.mockResolvedValueOnce(response({ items: [item], totalPages: 1, unreadCount: 1 }))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(response({ items: [{ ...item, readAt: "2026-10-16T09:00:00Z" }], totalPages: 1, unreadCount: 0 }));
  render(<MemoryRouter><NotificationsPage /></MemoryRouter>);
  expect((await screen.findByRole("link", { name: "View order →" })).getAttribute("href")).toBe("/bookings/b1");
  fireEvent.click(screen.getByRole("button", { name: "Mark as read" }));
  await screen.findByText("0 unread");
  expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/notifications/n1/read");
  expect(fetchMock.mock.calls[1][1]?.method).toBe("POST");
  expect(fetchMock.mock.calls[1][1]?.headers).toMatchObject({ Authorization: "Bearer token" });
});

it("shows failed reads as errors, not an empty inbox", async () => {
  fetchMock.mockResolvedValue(response({ detail: "Unavailable" }, 500));
  render(<MemoryRouter><NotificationsPage /></MemoryRouter>);
  await screen.findByRole("alert");
  expect(screen.queryByText("No notifications yet")).toBeNull();
});

it("filters unread items using the backend and offers sign in after session expiry", async () => {
  fetchMock.mockResolvedValueOnce(response({ items: [item], totalPages: 1, unreadCount: 1 }))
    .mockResolvedValueOnce(response({}, 401));
  render(<MemoryRouter><NotificationsPage /></MemoryRouter>);
  await screen.findByText(item.title);
  fireEvent.click(screen.getByRole("button", { name: "Unread" }));
  fireEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
  expect(fetchMock.mock.calls[1][0]).toContain("unreadOnly=true");
  expect(state.logout).toHaveBeenCalledOnce();
});
