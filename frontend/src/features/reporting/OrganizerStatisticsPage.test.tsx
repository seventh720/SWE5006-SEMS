// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { EditEventPage, OrganizerRoute } from "../events/OrganizerPages";
import { OrganizerStatisticsPage } from "./OrganizerStatisticsPage";

const auth = vi.hoisted(() => ({ token: "test-token", user: { roles: ["ORGANIZER"] }, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));
const fetchMock = vi.fn<typeof fetch>();
const statistics = { eventId: "event-1", eventStatus: "PUBLISHED", capacity: 200,
  validReservedTickets: 150, cancelledTickets: 12, checkedInTickets: 41 };
const draft = { id: "event-1", title: "My draft", description: "A community event", location: "Room A",
  startsAt: "2030-01-01T02:00:00Z", endsAt: "2030-01-01T04:00:00Z", capacity: 100, status: "DRAFT", version: 0,
  createdAt: "2029-12-01T00:00:00Z", updatedAt: "2029-12-01T00:00:00Z" };
function response(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }); }
function mount(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/" element={<p>Dashboard</p>} /><Route path="/login" element={<p>Sign-in page</p>} />
    <Route element={<OrganizerRoute />}>
      <Route path="/organizer/events" element={<p>My events</p>} />
      <Route path="/organizer/events/:id" element={<p>Event details</p>} />
      <Route path="/organizer/events/:id/statistics" element={<OrganizerStatisticsPage />} />
    </Route>
  </Routes></MemoryRouter>);
}
function LoginProbe() {
  const location = useLocation();
  const from = (location.state as { from?: { pathname?: string } } | null)?.from;
  return <p>{from?.pathname ?? "no-destination"}</p>;
}
beforeEach(() => { auth.user.roles = ["ORGANIZER"]; auth.logout.mockClear(); fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("organizer statistics page", () => {
  it("renders capacity, reserved, cancelled and checked-in statistics", async () => {
    fetchMock.mockResolvedValue(response(statistics));
    mount("/organizer/events/event-1/statistics");
    await screen.findByText("Valid reserved tickets");
    expect(screen.getByText("Capacity")).toBeTruthy();
    expect(screen.getByText("Cancelled tickets")).toBeTruthy();
    expect(screen.getByText("Checked-in tickets")).toBeTruthy();
    expect(screen.getByText("200")).toBeTruthy();
    expect(screen.getByText("150")).toBeTruthy();
    expect(screen.getByText("12")).toBeTruthy();
    expect(screen.getByText("41")).toBeTruthy();
    expect(screen.getByText("PUBLISHED")).toBeTruthy();
  });

  it("shows zero ticket statistics as valid data, not missing", async () => {
    fetchMock.mockResolvedValue(response({ ...statistics, eventStatus: "DRAFT", validReservedTickets: 0, cancelledTickets: 0, checkedInTickets: 0 }));
    mount("/organizer/events/event-1/statistics");
    await screen.findByText("Valid reserved tickets");
    expect(screen.getAllByText("0")).toHaveLength(3);
    expect(screen.getByText("200")).toBeTruthy();
    expect(screen.getByText("DRAFT")).toBeTruthy();
  });

  it("shows a cancelled event as still reportable", async () => {
    fetchMock.mockResolvedValue(response({ ...statistics, eventStatus: "CANCELLED", validReservedTickets: 0, cancelledTickets: 150, checkedInTickets: 0 }));
    mount("/organizer/events/event-1/statistics");
    await screen.findByText("Cancelled tickets");
    expect(screen.getByText("CANCELLED")).toBeTruthy();
    expect(screen.getByText("150")).toBeTruthy();
    expect(screen.getAllByText("0")).toHaveLength(2);
  });

  it("requests the statistics endpoint with the bearer token", async () => {
    fetchMock.mockResolvedValue(response(statistics));
    mount("/organizer/events/event-1/statistics");
    await screen.findByText("Valid reserved tickets");
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/organizer/events/event-1/statistics");
    expect(options?.headers).toMatchObject({ Authorization: "Bearer test-token" });
  });

  it.each(["ORGANIZER", "ADMIN"])("allows %s to view statistics", async (role) => {
    auth.user.roles = [role];
    fetchMock.mockResolvedValue(response(statistics));
    mount("/organizer/events/event-1/statistics");
    await screen.findByText("Valid reserved tickets");
  });

  it.each(["ATTENDEE", "STAFF"])("redirects %s away from statistics", (role) => {
    auth.user.roles = [role];
    mount("/organizer/events/event-1/statistics");
    expect(screen.getByText("Dashboard")).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("offers sign in again and preserves the return destination on expired session", async () => {
    fetchMock.mockResolvedValue(response({}, 401));
    render(<MemoryRouter initialEntries={["/organizer/events/event-1/statistics"]}><Routes>
      <Route path="/login" element={<LoginProbe />} />
      <Route element={<OrganizerRoute />}>
        <Route path="/organizer/events/:id/statistics" element={<OrganizerStatisticsPage />} />
      </Route>
    </Routes></MemoryRouter>);
    fireEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    expect(auth.logout).toHaveBeenCalledOnce();
    await screen.findByText("/organizer/events/event-1/statistics");
  });

  it("shows permission feedback without logging out on forbidden", async () => {
    fetchMock.mockResolvedValue(response({}, 403));
    mount("/organizer/events/event-1/statistics");
    await screen.findByText(/organizer or administrator role/i);
    expect(screen.getByRole("link", { name: "Return to dashboard" }).getAttribute("href")).toBe("/");
    expect(screen.queryByRole("button", { name: "Sign in again" })).toBeNull();
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it("does not reveal whether the event exists on not-found", async () => {
    fetchMock.mockResolvedValue(response({}, 404));
    mount("/organizer/events/event-1/statistics");
    await screen.findByText("This event is unavailable or does not belong to your account.");
    expect(screen.queryByText(/does not exist/i)).toBeNull();
    expect(screen.getByRole("button", { name: "Try again" })).toBeTruthy();
  });

  it("recovers from a network failure by retrying", async () => {
    fetchMock.mockRejectedValueOnce(new Error("network down")).mockResolvedValueOnce(response(statistics));
    mount("/organizer/events/event-1/statistics");
    await screen.findByText(/couldn't complete the request/i);
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    await screen.findByText("Valid reserved tickets");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("links back to My events and the event management page", async () => {
    fetchMock.mockResolvedValue(response(statistics));
    mount("/organizer/events/event-1/statistics");
    await screen.findByText("Valid reserved tickets");
    expect(screen.getByRole("link", { name: "← My events" }).getAttribute("href")).toBe("/organizer/events");
    expect(screen.getByRole("link", { name: "← Event details" }).getAttribute("href")).toBe("/organizer/events/event-1");
    fireEvent.click(screen.getByRole("link", { name: "← Event details" }));
    await screen.findByText("Event details");
  });

  it("links from event management to the statistics page", async () => {
    fetchMock.mockResolvedValue(response(draft));
    render(<MemoryRouter initialEntries={["/organizer/events/event-1"]}><Routes>
      <Route element={<OrganizerRoute />}>
        <Route path="/organizer/events/:id" element={<EditEventPage />} />
      </Route>
    </Routes></MemoryRouter>);
    const link = await screen.findByRole("link", { name: "View statistics →" });
    expect(link.getAttribute("href")).toBe("/organizer/events/event-1/statistics");
  });
});
