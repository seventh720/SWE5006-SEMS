// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { OrganizerOrdersPage } from "./OrganizerOrdersPage";
import { OrganizerRoute } from "../events/OrganizerPages";

const auth = vi.hoisted(() => ({ token: "test-token", user: { roles: ["ORGANIZER"] }, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));
const fetchMock = vi.fn<typeof fetch>();
function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
function mount() {
  render(<MemoryRouter initialEntries={["/organizer/events/e1/bookings"]}><Routes>
    <Route path="/" element={<p>Dashboard</p>} />
    <Route element={<OrganizerRoute />}><Route path="/organizer/events/:id/bookings" element={<OrganizerOrdersPage />} /></Route>
  </Routes></MemoryRouter>);
}
beforeEach(() => { auth.user.roles = ["ORGANIZER"]; fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it("shows order status and attendee identifiers with authenticated pagination", async () => {
  fetchMock.mockImplementation(async () => response({ items: [{ id: "b1", attendeeId: "u1", ticketTypeName: "General", quantity: 2,
    status: "CANCELLED", cancellationReason: "EVENT_CANCELLED", createdAt: "2026-10-06T01:00:00Z" }], totalElements: 11, totalPages: 2 }));
  mount();
  expect(await screen.findByText("Event cancelled")).toBeTruthy();
  expect(screen.getByText("Attendee: u1")).toBeTruthy();
  expect(fetchMock.mock.calls[0][1]?.headers).toMatchObject({ Authorization: "Bearer test-token" });
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await screen.findByText("Page 2 of 2");
  expect(fetchMock.mock.calls.at(-1)?.[0]).toBe("/api/v1/organizer/events/e1/bookings?page=1&size=10");
});

it("shows the existing ownership error for another organizer's event", async () => {
  fetchMock.mockResolvedValue(response({ detail: "Event not found" }, 404));
  mount();
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(screen.queryByText("Attendee: u1")).toBeNull();
});

it("does not load organizer orders for staff-only users", () => {
  auth.user.roles = ["STAFF"];
  mount();
  expect(screen.getByText("Dashboard")).toBeTruthy();
  expect(fetchMock).not.toHaveBeenCalled();
});
