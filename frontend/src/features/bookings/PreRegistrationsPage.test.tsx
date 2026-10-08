// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { PreRegistrationsPage } from "./PreRegistrationsPage";
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ token: "token", logout: vi.fn() }) }));
const fetchMock = vi.fn<typeof fetch>();
const pre = { id: "p1", eventId: "e1", eventTitle: "Scheduled workshop", ticketTypeId: "t1", ticketTypeName: "Free", quantity: 2,
  attendeeInfo: {}, registrationOpensAt: "2030-01-01T00:01:00Z", registrationClosesAt: "2030-01-02T00:00:00Z", status: "WAITING" };
function response(items: unknown[]) { return new Response(JSON.stringify({ items, totalElements: items.length, totalPages: 1 }), { status: 200 }); }
function mount() { render(<MemoryRouter><PreRegistrationsPage /></MemoryRouter>); }
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); });
it("counts down to opening and enables review without automatically booking", async () => {
  vi.useFakeTimers(); vi.setSystemTime(new Date("2030-01-01T00:00:00Z"));
  fetchMock.mockResolvedValue(response([pre]));
  await act(async () => { mount(); });
  expect(screen.getByLabelText("Countdown to registration").textContent).toContain("01m 00s");
  expect(screen.getByRole("link", { name: "Edit pre-registration" })).toBeTruthy();
  await act(async () => { vi.advanceTimersByTime(60001); });
  expect(screen.getByRole("link", { name: "Review and book" }).getAttribute("href")).toBe("/events/e1?preRegistration=t1");
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
it("removes only the saved entry and refreshes the list", async () => {
  fetchMock.mockResolvedValueOnce(response([{ ...pre, status: "OPEN" }]))
    .mockResolvedValueOnce(new Response(null, { status: 204 })).mockResolvedValueOnce(response([]));
  mount();
  fireEvent.click(await screen.findByRole("button", { name: "Remove saved entry" }));
  await screen.findByText("No pre-registrations on this page.");
  expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/pre-registrations/e1");
  expect(fetchMock.mock.calls[1][1]?.method).toBe("DELETE");
});
it("does not offer booking for cancelled or closed events and links completed entries to orders", async () => {
  fetchMock.mockResolvedValue(response([
    { ...pre, status: "EVENT_CANCELLED" }, { ...pre, id: "p2", status: "CLOSED" },
    { ...pre, id: "p3", status: "BOOKED", bookingId: "b1" },
  ]));
  mount();
  await screen.findByText("This event was cancelled.");
  expect(screen.getByText("Registration has closed.")).toBeTruthy();
  expect(screen.queryByRole("link", { name: "Review and book" })).toBeNull();
  expect(screen.getByRole("link", { name: "View order →" }).getAttribute("href")).toBe("/bookings/b1");
});
