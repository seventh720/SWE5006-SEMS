// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { TicketTypesSection } from "./TicketTypesSection";

const auth = vi.hoisted(() => ({
  token: "test-token" as string | null,
  user: { roles: ["ATTENDEE"] } as { roles: string[] } | null,
  logout: vi.fn(),
}));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));

const fetchMock = vi.fn<typeof fetch>();
const free = { id: "t1", name: "General admission", priceMinor: 0, currency: "SGD", quota: 10, bookedQuantity: 3 };
const paid = { id: "t2", name: "VIP", priceMinor: 5000, currency: "SGD", quota: 5, bookedQuantity: 1 };
function response(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status }); }
function LoginProbe() {
  const location = useLocation();
  const from = (location.state as { from?: { pathname?: string; search?: string } } | null)?.from;
  return <p>Return to {from?.pathname ? `${from.pathname}${from.search ?? ""}` : "none"}</p>;
}
function mount() {
  return render(<MemoryRouter initialEntries={["/events/e1"]}><Routes>
    <Route path="/events/:id" element={<TicketTypesSection eventId="e1" />} />
    <Route path="/login" element={<LoginProbe />} />
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

it("renders free and paid ticket types with price and availability", async () => {
  fetchMock.mockResolvedValue(response([free, paid]));
  mount();
  expect(await screen.findByText("General admission")).toBeTruthy();
  expect(screen.getByText("Free")).toBeTruthy();
  expect(screen.getByText("7 of 10 available")).toBeTruthy();
  expect(screen.getByText("S$0.00")).toBeTruthy();
  expect(screen.getByText("VIP")).toBeTruthy();
  expect(screen.getByText("Paid")).toBeTruthy();
  expect(screen.getByText("4 of 5 available")).toBeTruthy();
  expect(screen.getByText("S$50.00")).toBeTruthy();
  expect(screen.getByText("Booking for paid tickets is not available yet.")).toBeTruthy();
  // Only the free ticket exposes a reservation action.
  expect(screen.getAllByRole("button", { name: "Reserve" })).toHaveLength(1);
});

it("marks a sold-out ticket type and offers no reservation", async () => {
  fetchMock.mockResolvedValue(response([{ ...free, quota: 2, bookedQuantity: 2 }]));
  mount();
  expect(await screen.findByText("Sold out")).toBeTruthy();
  expect(screen.queryByText(/available/)).toBeNull();
  expect(screen.queryByRole("button", { name: "Reserve" })).toBeNull();
});

it("shows an empty state when no ticket types exist", async () => {
  fetchMock.mockResolvedValue(response([]));
  mount();
  expect(await screen.findByText("Booking is not available yet.")).toBeTruthy();
});

it("shows an error and retries", async () => {
  fetchMock.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(response([free]));
  mount();
  fireEvent.click(await screen.findByRole("button", { name: "Try again" }));
  expect(await screen.findByText("General admission")).toBeTruthy();
});

it("refreshes availability after a successful reservation", async () => {
  let getCount = 0;
  fetchMock.mockImplementation(async (_url, options) => {
    if (options?.method === "POST") return response({ id: "booking-1" }, 201);
    getCount += 1;
    return response(getCount === 1 ? [{ ...free, quota: 1, bookedQuantity: 0 }] : [{ ...free, quota: 1, bookedQuantity: 1 }]);
  });
  mount();
  await screen.findByText("1 of 1 available");
  fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
  await screen.findByText("Reservation confirmed.");
  expect(await screen.findByText("Sold out")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Reserve" })).toBeNull();
});

it("redirects anonymous users to login and preserves the return destination", async () => {
  auth.user = null;
  auth.token = null;
  fetchMock.mockResolvedValue(response([free]));
  mount();
  await screen.findByText("General admission");
  fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
  expect(await screen.findByText("Return to /events/e1")).toBeTruthy();
  expect(fetchMock.mock.calls.some(([, options]) => options?.method === "POST")).toBe(false);
});
