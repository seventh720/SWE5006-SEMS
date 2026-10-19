// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { EventReportsPage } from "./EventReportsPage";
const auth = vi.hoisted(() => ({ token: "token", user: { roles: ["ADMIN"] }, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));
const fetchMock = vi.fn<typeof fetch>();
const report = { items: [{ eventId: "event", eventTitle: "Workshop", eventStatus: "PUBLISHED", startsAt: "2030-01-01T02:00:00Z", endsAt: "2030-01-01T04:00:00Z", ticketTypeId: "ticket", ticketType: "General", quota: 100, available: 90, confirmed: 10, cancelled: 2, checkedIn: 3 }], totalElements: 11, totalPages: 2, totals: { confirmed: 120, cancelled: 22, checkedIn: 33 } };
function mount(admin = false) { render(<MemoryRouter initialEntries={["/reports"]}><Routes><Route path="/reports" element={<EventReportsPage admin={admin} />} /><Route path="/" element={<h1>Dashboard</h1>} /></Routes></MemoryRouter>); }
beforeEach(() => { auth.user.roles = ["ADMIN"]; fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it("uses global admin scope and shows totals beyond the current page", async () => {
  fetchMock.mockImplementation(async () => new Response(JSON.stringify(report)));
  mount(true); await screen.findByText("Workshop");
  expect(String(fetchMock.mock.calls[0][0])).toContain("/api/v1/admin/reports?");
  expect(screen.getByLabelText("Report totals").textContent).toContain("120");
  fireEvent.change(screen.getByLabelText("Ticket type"), { target: { value: "General" } });
  fireEvent.click(screen.getByRole("button", { name: "Apply filters" }));
  await waitFor(() => expect(String(fetchMock.mock.calls.at(-1)![0])).toContain("ticketType=General"));
  await screen.findByText("Workshop");
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await screen.findByText("Page 2 of 2");
  expect(String(fetchMock.mock.calls.at(-1)![0])).toContain("page=1");
  expect(String(fetchMock.mock.calls.at(-1)![0])).toContain("ticketType=General");
});
it("uses the owner endpoint for an admin's own report", async () => {
  fetchMock.mockImplementation(async () => new Response(JSON.stringify(report))); mount(); await screen.findByText("Workshop");
  expect(String(fetchMock.mock.calls[0][0])).toContain("/api/v1/organizer/reports?");
});
it("prevents organizers requesting global report data", () => {
  auth.user.roles = ["ORGANIZER"]; mount(true);
  expect(screen.getByRole("heading", { name: "Dashboard" })).toBeTruthy(); expect(fetchMock).not.toHaveBeenCalled();
});
it("validates reversed dates without a request", async () => {
  fetchMock.mockImplementation(async () => new Response(JSON.stringify(report))); mount(); await screen.findByText("Workshop");
  fireEvent.change(screen.getByLabelText("From date (SGT)"), { target: { value: "2030-02-01" } });
  fireEvent.change(screen.getByLabelText("To date (SGT)"), { target: { value: "2030-01-01" } });
  fireEvent.click(screen.getByRole("button", { name: "Apply filters" }));
  expect(screen.getByRole("alert").textContent).toContain("From date"); expect(fetchMock).toHaveBeenCalledOnce();
});
it("offers reauthentication after an expired request", async () => {
  fetchMock.mockResolvedValue(new Response("{}", { status: 401 })); mount();
  expect(await screen.findByRole("button", { name: "Sign in again" })).toBeTruthy();
});
