// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AttendeeRoute, MyOrdersPage } from "./MyOrdersPage";

const auth = vi.hoisted(() => ({ token: "test-token", user: { roles: ["ATTENDEE"] }, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));

const fetchMock = vi.fn<typeof fetch>();
function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const booking = {
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

function page(items: unknown[], page = 0, totalElements = items.length, totalPages = 1) {
  return { items, page, size: 10, totalElements, totalPages };
}

function mount(path = "/bookings") {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/" element={<p>Dashboard</p>} />
    <Route path="/login" element={<p>Sign-in page</p>} />
    <Route element={<AttendeeRoute />}>
      <Route path="/bookings" element={<MyOrdersPage />} />
    </Route>
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

describe("My Orders", () => {
  it("loads the attendee's bookings", async () => {
    fetchMock.mockResolvedValue(response(page([booking])));
    mount();
    expect(await screen.findByText("Order b1")).toBeTruthy();
    expect(screen.getByRole("link", { name: "View order →" }).getAttribute("href")).toBe("/bookings/b1");
  });

  it("renders booking id, event snapshot, ticket type, quantity, total and status", async () => {
    fetchMock.mockResolvedValue(response(page([booking])));
    mount();
    await screen.findByText("Order b1");
    expect(screen.getByText("Open Day")).toBeTruthy();
    expect(screen.getByText("General admission")).toBeTruthy();
    expect(screen.getByText("2 tickets")).toBeTruthy();
    expect(screen.getByText("Free")).toBeTruthy();
    expect(screen.getByText("Confirmed")).toBeTruthy();
  });

  it("shows an empty state when there are no orders", async () => {
    fetchMock.mockResolvedValue(response(page([])));
    mount();
    expect(await screen.findByText("No orders yet")).toBeTruthy();
    expect(screen.queryByRole("navigation")).toBeNull();
  });

  it("paginates forward and backward", async () => {
    fetchMock.mockImplementation(async (input) => {
      const url = new URL(String(input), "http://test");
      const pageNumber = Number(url.searchParams.get("page") ?? "0");
      return response(pageNumber === 0
        ? { items: [booking], page: 0, size: 10, totalElements: 11, totalPages: 2 }
        : { items: [{ ...booking, id: "b11" }], page: 1, size: 10, totalElements: 11, totalPages: 2 });
    });
    mount();
    await screen.findByText("Order b1");
    expect(screen.getByText("Page 1 of 2")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(await screen.findByText("Order b11")).toBeTruthy();
    expect(screen.getByText("Page 2 of 2")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Previous" }));
    expect(await screen.findByText("Order b1")).toBeTruthy();
    expect(screen.getByText("Page 1 of 2")).toBeTruthy();
  });

  it("sends the attendee token with the list request", async () => {
    fetchMock.mockResolvedValue(response(page([booking])));
    mount();
    await screen.findByText("Order b1");
    const [url, options] = fetchMock.mock.calls[0];
    expect(String(url)).toBe("/api/v1/bookings?page=0&size=10");
    expect(options?.headers).toMatchObject({ Authorization: "Bearer test-token" });
  });

  it("allows a multi-role user that includes attendee", async () => {
    auth.user = { roles: ["ORGANIZER", "ATTENDEE"] };
    fetchMock.mockResolvedValue(response(page([booking])));
    mount();
    expect(await screen.findByText("Order b1")).toBeTruthy();
  });

  it("blocks a user without the attendee role", async () => {
    auth.user = { roles: ["ORGANIZER"] };
    mount();
    expect(screen.getByText("Dashboard")).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows a return-to-dashboard path instead of sign-in for a forbidden list load", async () => {
    fetchMock.mockResolvedValue(response({}, 403));
    mount();
    expect(await screen.findByText("An attendee role is required to view bookings.")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Return to dashboard" }).getAttribute("href")).toBe("/");
    expect(screen.queryByRole("button", { name: "Sign in again" })).toBeNull();
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it("recovers from a failed load with a retry", async () => {
    fetchMock.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(response(page([booking])));
    mount();
    expect(await screen.findByText("We couldn't load bookings right now. Please try again.")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("Order b1")).toBeTruthy();
  });
});
