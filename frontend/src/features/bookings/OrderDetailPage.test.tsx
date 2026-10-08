// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { OrderDetailPage } from "./OrderDetailPage";

const auth = vi.hoisted(() => ({ token: "test-token", user: { roles: ["ATTENDEE"] }, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));

const fetchMock = vi.fn<typeof fetch>();
function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const confirmed = {
  id: "b1",
  eventTitle: "Open Day",
  eventLocation: "Singapore",
  eventStartsAt: "2030-01-01T02:00:00Z",
  eventEndsAt: "2030-01-01T04:00:00Z",
  ticketTypeName: "General admission",
  quantity: 2,
  unitPriceMinor: 0,
  totalAmountMinor: 0,
  currency: "SGD",
  status: "CONFIRMED",
  paymentStatus: "NOT_REQUIRED",
  createdAt: "2030-01-01T00:00:00Z",
};

function mount(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/" element={<p>Dashboard</p>} />
    <Route path="/login" element={<p>Sign-in page</p>} />
    <Route path="/bookings" element={<p>My orders list</p>} />
    <Route path="/bookings/:id" element={<OrderDetailPage />} />
  </Routes></MemoryRouter>);
}

beforeEach(() => {
  auth.token = "test-token";
  auth.user = { roles: ["ATTENDEE"] };
  auth.logout.mockClear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("Order detail", () => {
  it("renders the booking snapshot without fetching public event details", async () => {
    fetchMock.mockResolvedValue(response(confirmed));
    mount("/bookings/b1");
    await screen.findByText("Open Day");
    expect(screen.getByText("b1")).toBeTruthy();
    expect(screen.getByText("Confirmed")).toBeTruthy();
    expect(screen.getByText("General admission")).toBeTruthy();
    expect(screen.getByText("2")).toBeTruthy();
    expect(screen.getAllByText("Free")).toHaveLength(2);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toBe("/api/v1/bookings/b1");
  });

  it("shows the latest event arrangement after an organizer edits a published event", async () => {
    fetchMock.mockResolvedValue(response({ ...confirmed, currentEvent: {
      title: "Updated Open Day", location: "New venue",
      startsAt: "2031-02-01T02:00:00Z", endsAt: "2031-02-01T04:00:00Z",
    } }));
    mount("/bookings/b1");
    expect(await screen.findByText("Updated Open Day")).toBeTruthy();
    expect(screen.getByText("New venue")).toBeTruthy();
    expect(screen.queryByText("Singapore")).toBeNull();
    expect(screen.getAllByText(/2031/).length).toBeGreaterThan(0);
    expect(screen.getByText("General admission")).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("shows non-leaking feedback for a missing booking", async () => {
    fetchMock.mockResolvedValue(response({ detail: "Booking not found" }, 404));
    mount("/bookings/b1");
    expect(await screen.findByText("This booking is unavailable or does not belong to your account.")).toBeTruthy();
    expect(screen.queryByText("Open Day")).toBeNull();
  });

  it("offers sign-in-again when the session expires", async () => {
    fetchMock.mockResolvedValue(response({}, 401));
    mount("/bookings/b1");
    fireEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    expect(auth.logout).toHaveBeenCalledOnce();
    expect(screen.getByText("Sign-in page")).toBeTruthy();
  });

  it("offers a return to the dashboard for a forbidden booking, not a sign-in", async () => {
    fetchMock.mockResolvedValue(response({}, 403));
    mount("/bookings/b1");
    expect(await screen.findByText("An attendee role is required to view bookings.")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Return to dashboard" }).getAttribute("href")).toBe("/");
    expect(screen.queryByRole("button", { name: "Sign in again" })).toBeNull();
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it("exposes cancellation for a confirmed booking", async () => {
    fetchMock.mockResolvedValue(response(confirmed));
    mount("/bookings/b1");
    expect(await screen.findByRole("button", { name: "Cancel reservation" })).toBeTruthy();
  });

  it("does not send a cancellation request until confirmed", async () => {
    fetchMock.mockResolvedValue(response(confirmed));
    mount("/bookings/b1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel reservation" }));
    expect(screen.getByRole("button", { name: "Confirm cancellation" })).toBeTruthy();
    expect(fetchMock.mock.calls.some(([, options]) => options?.method === "POST")).toBe(false);
  });

  it("cancels through the booking cancel endpoint", async () => {
    fetchMock.mockImplementation(async (_url, options) =>
      options?.method === "POST"
        ? response({ ...confirmed, status: "CANCELLED", cancellationReason: "Attendee cancelled" })
        : response(confirmed));
    mount("/bookings/b1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel reservation" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm cancellation" }));
    await screen.findByText("Cancelled");
    const post = fetchMock.mock.calls.find(([, options]) => options?.method === "POST")!;
    expect(post[0]).toBe("/api/v1/bookings/b1/cancel");
  });

  it("sends the bearer token when cancelling", async () => {
    fetchMock.mockImplementation(async (_url, options) =>
      options?.method === "POST" ? response({ ...confirmed, status: "CANCELLED" }) : response(confirmed));
    mount("/bookings/b1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel reservation" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm cancellation" }));
    await screen.findByText("Cancelled");
    const post = fetchMock.mock.calls.find(([, options]) => options?.method === "POST")!;
    expect(post[1]?.headers).toMatchObject({ Authorization: "Bearer test-token" });
  });

  it("prevents duplicate cancellation requests while pending", async () => {
    let resolvePost!: (value: Response) => void;
    fetchMock.mockImplementation(async (_url, options) => {
      if (options?.method === "POST") return new Promise((resolve) => { resolvePost = resolve; });
      return response(confirmed);
    });
    mount("/bookings/b1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel reservation" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm cancellation" }));
    expect((screen.getByRole("button", { name: "Cancelling…" }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Cancelling…" }));
    resolvePost(response({ ...confirmed, status: "CANCELLED" }));
    await screen.findByText("Cancelled");
    expect(fetchMock.mock.calls.filter(([, options]) => options?.method === "POST")).toHaveLength(1);
  });

  it("renders cancelled state and removes the cancel action on success", async () => {
    fetchMock.mockImplementation(async (_url, options) =>
      options?.method === "POST"
        ? response({ ...confirmed, status: "CANCELLED", cancellationReason: "Attendee cancelled" })
        : response(confirmed));
    mount("/bookings/b1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel reservation" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm cancellation" }));
    await screen.findByText("This reservation has been cancelled.");
    expect(screen.getByText("Cancelled")).toBeTruthy();
    expect(screen.getByText("Attendee cancelled")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Cancel reservation" })).toBeNull();
  });

  it("does not offer cancellation for an already-cancelled booking", async () => {
    fetchMock.mockResolvedValue(response({ ...confirmed, status: "CANCELLED", cancellationReason: "Event cancelled" }));
    mount("/bookings/b1");
    await screen.findByText("Cancelled");
    expect(screen.queryByRole("button", { name: "Cancel reservation" })).toBeNull();
  });

  it("accepts a backend-returned already-cancelled result without an error", async () => {
    fetchMock.mockImplementation(async (_url, options) =>
      options?.method === "POST" ? response({ ...confirmed, status: "CANCELLED" }) : response(confirmed));
    mount("/bookings/b1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel reservation" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm cancellation" }));
    await screen.findByText("Cancelled");
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByRole("button", { name: "Cancel reservation" })).toBeNull();
  });

  it("surfaces a cancellation conflict without marking the booking cancelled", async () => {
    fetchMock.mockImplementation(async (_url, options) =>
      options?.method === "POST" ? response({ detail: "The event has already started" }, 409) : response(confirmed));
    mount("/bookings/b1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel reservation" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm cancellation" }));
    expect(await screen.findByText("The event has already started")).toBeTruthy();
    expect(screen.getByText("Confirmed")).toBeTruthy();
    expect(screen.queryByText("Cancelled")).toBeNull();
    expect(screen.getByRole("button", { name: "Cancel reservation" })).toBeTruthy();
  });

  it("does not locally mark cancelled on an uncertain failure and recovers via reload", async () => {
    let getCount = 0;
    fetchMock.mockImplementation(async (_url, options) => {
      if (options?.method === "POST") throw new Error("network");
      getCount += 1;
      return response(getCount === 1 ? confirmed : { ...confirmed, status: "CANCELLED", cancellationReason: "Event cancelled" });
    });
    mount("/bookings/b1");
    fireEvent.click(await screen.findByRole("button", { name: "Cancel reservation" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm cancellation" }));
    expect(await screen.findByText("We couldn't confirm your cancellation. Please reload to check the latest status.")).toBeTruthy();
    expect(screen.getByText("Confirmed")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Reload" }));
    expect(await screen.findByText("Cancelled")).toBeTruthy();
  });
});
