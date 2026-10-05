import { describe, expect, it } from "vitest";
import {
  formatSgdPrice,
  isFree,
  isManagedTicketType,
  isSoldOut,
  minorToSgdInput,
  organizerTicketTypePath,
  organizerTicketTypesPath,
  parseSgdPriceToMinor,
  publicTicketTypesPath,
  remainingFor,
  SGD_CURRENCY,
  type TicketType,
} from "./ticketTypes";

const free: TicketType = { id: "t1", name: "General admission", priceMinor: 0, currency: "SGD", quota: 10, bookedQuantity: 3 };

describe("ticket type helpers", () => {
  it("converts SGD dollar amounts to minor units and back", () => {
    expect(parseSgdPriceToMinor("12.50")).toBe(1250);
    expect(parseSgdPriceToMinor("0")).toBe(0);
    expect(parseSgdPriceToMinor("0.00")).toBe(0);
    expect(parseSgdPriceToMinor("5")).toBe(500);
    expect(parseSgdPriceToMinor("5.5")).toBe(550);
    expect(parseSgdPriceToMinor("12.05")).toBe(1205);
    expect(minorToSgdInput(1250)).toBe("12.50");
    expect(minorToSgdInput(0)).toBe("0.00");
    expect(minorToSgdInput(5)).toBe("0.05");
  });

  it("rejects invalid price input", () => {
    expect(parseSgdPriceToMinor("")).toBeNull();
    expect(parseSgdPriceToMinor("-1")).toBeNull();
    expect(parseSgdPriceToMinor("1.234")).toBeNull();
    expect(parseSgdPriceToMinor("abc")).toBeNull();
    expect(parseSgdPriceToMinor("1,000")).toBeNull();
    expect(parseSgdPriceToMinor(".5")).toBeNull();
  });

  it("derives availability from quota and booked quantity, not event capacity", () => {
    expect(remainingFor(free)).toBe(7);
    expect(isSoldOut(free)).toBe(false);
    expect(isFree(free)).toBe(true);
    expect(isSoldOut({ ...free, bookedQuantity: 10 })).toBe(true);
    expect(remainingFor({ ...free, bookedQuantity: 12 })).toBe(0);
    expect(isFree({ ...free, priceMinor: 100 })).toBe(false);
  });

  it("requires a backend-supplied version to treat a ticket type as editable", () => {
    expect(isManagedTicketType({ ...free, version: 0 })).toBe(true);
    expect(isManagedTicketType(free)).toBe(false);
  });

  it("formats prices with the Singapore dollar symbol", () => {
    expect(formatSgdPrice(0)).toBe("S$0.00");
    expect(formatSgdPrice(1250)).toBe("S$12.50");
    expect(formatSgdPrice(5)).toBe("S$0.05");
  });

  it("builds API paths with encoded ids", () => {
    expect(SGD_CURRENCY).toBe("SGD");
    expect(publicTicketTypesPath("e1")).toBe("/api/v1/events/e1/ticket-types");
    expect(organizerTicketTypesPath("e1")).toBe("/api/v1/organizer/events/e1/ticket-types");
    expect(organizerTicketTypePath("e1", "t1")).toBe("/api/v1/organizer/events/e1/ticket-types/t1");
  });
});
