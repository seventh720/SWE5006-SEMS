// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { TicketTypesSection } from "./TicketTypesSection";

const fetchMock = vi.fn<typeof fetch>();
const free = { id: "t1", name: "General admission", priceMinor: 0, currency: "SGD", quota: 10, bookedQuantity: 3 };
const paid = { id: "t2", name: "VIP", priceMinor: 5000, currency: "SGD", quota: 5, bookedQuantity: 1 };
function response(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status }); }
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it("renders free and paid ticket types with price and availability", async () => {
  fetchMock.mockResolvedValue(response([free, paid]));
  render(<TicketTypesSection eventId="e1" />);
  expect(await screen.findByText("General admission")).toBeTruthy();
  expect(screen.getByText("Free")).toBeTruthy();
  expect(screen.getByText("7 of 10 available")).toBeTruthy();
  expect(screen.getByText("S$0.00")).toBeTruthy();
  expect(screen.getByText("VIP")).toBeTruthy();
  expect(screen.getByText("Paid")).toBeTruthy();
  expect(screen.getByText("4 of 5 available")).toBeTruthy();
  expect(screen.getByText("S$50.00")).toBeTruthy();
  expect(screen.getByText("Booking for paid tickets is not available yet.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: /book|reserve/i })).toBeNull();
});

it("marks a sold-out ticket type", async () => {
  fetchMock.mockResolvedValue(response([{ ...free, quota: 2, bookedQuantity: 2 }]));
  render(<TicketTypesSection eventId="e1" />);
  expect(await screen.findByText("Sold out")).toBeTruthy();
  expect(screen.queryByText(/available/)).toBeNull();
});

it("shows an empty state when no ticket types exist", async () => {
  fetchMock.mockResolvedValue(response([]));
  render(<TicketTypesSection eventId="e1" />);
  expect(await screen.findByText("Booking is not available yet.")).toBeTruthy();
});

it("shows an error and retries", async () => {
  fetchMock.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(response([free]));
  render(<TicketTypesSection eventId="e1" />);
  fireEvent.click(await screen.findByRole("button", { name: "Try again" }));
  expect(await screen.findByText("General admission")).toBeTruthy();
});
