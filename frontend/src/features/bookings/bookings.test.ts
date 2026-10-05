import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api/client";
import {
  bookingError,
  bookingPath,
  bookingsPath,
  cancelBookingError,
  cancelBookingPath,
  classifyReservationError,
  formatBookingAmount,
  newIdempotencyKey,
  readBookingPage,
  validateBookingQuantity,
} from "./bookings";

describe("newIdempotencyKey", () => {
  it("returns a non-empty, fresh key for each call", () => {
    const first = newIdempotencyKey();
    const second = newIdempotencyKey();
    expect(first).not.toBe("");
    expect(second).not.toBe("");
    expect(first).not.toBe(second);
  });

  it("uses crypto.randomUUID when available", () => {
    const randomUUID = vi.fn(() => "fixed-key");
    vi.stubGlobal("crypto", { randomUUID });
    try {
      expect(newIdempotencyKey()).toBe("fixed-key");
      expect(randomUUID).toHaveBeenCalledOnce();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("uses crypto.getRandomValues when randomUUID is unavailable", () => {
    const getRandomValues = vi.fn((array: Uint8Array) => {
      for (let i = 0; i < array.length; i++) array[i] = i;
      return array;
    });
    vi.stubGlobal("crypto", { getRandomValues });
    try {
      expect(newIdempotencyKey()).toBe("000102030405060708090a0b0c0d0e0f");
      expect(getRandomValues).toHaveBeenCalledOnce();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("throws instead of generating a weak key when no secure source exists", () => {
    vi.stubGlobal("crypto", {});
    try {
      expect(() => newIdempotencyKey()).toThrow(/secure crypto source/i);
    } finally {
      vi.unstubAllGlobals();
    }
  });
});

describe("validateBookingQuantity", () => {
  it("accepts positive whole numbers within remaining availability", () => {
    expect(validateBookingQuantity("1", 7)).toBeNull();
    expect(validateBookingQuantity("7", 7)).toBeNull();
    expect(validateBookingQuantity("10", 12)).toBeNull();
  });

  it("rejects non-numeric and non-whole input", () => {
    expect(validateBookingQuantity("", 7)).toBe("Enter a whole number of tickets.");
    expect(validateBookingQuantity("abc", 7)).toBe("Enter a whole number of tickets.");
    expect(validateBookingQuantity("1.5", 7)).toBe("Enter a whole number of tickets.");
    expect(validateBookingQuantity("-1", 7)).toBe("Enter a whole number of tickets.");
  });

  it("rejects zero and negative quantities", () => {
    expect(validateBookingQuantity("0", 7)).toBe("Enter a positive whole number of tickets.");
  });

  it("rejects quantities above remaining availability before the per-order cap", () => {
    expect(validateBookingQuantity("8", 7)).toBe("Only 7 of these tickets remain.");
  });

  it("caps a single order at ten tickets", () => {
    expect(validateBookingQuantity("11", 20)).toBe("You can reserve up to 10 tickets per order.");
  });
});

describe("classifyReservationError", () => {
  it("surfaces backend field errors for a 400", () => {
    const failure = classifyReservationError(new ApiError(400, { fieldErrors: { quantity: "Quantity too high" } }));
    expect(failure).toEqual({ kind: "invalid", message: "Quantity too high", retryable: false });
  });

  it("falls back to the problem detail for a 400 without field errors", () => {
    const failure = classifyReservationError(new ApiError(400, { detail: "Bad request" }));
    expect(failure.kind).toBe("invalid");
    expect(failure.message).toBe("Bad request");
    expect(failure.retryable).toBe(false);
  });

  it("maps 401/403/404 to definite, non-retryable failures", () => {
    expect(classifyReservationError(new ApiError(401, {})).kind).toBe("auth");
    expect(classifyReservationError(new ApiError(403, {})).kind).toBe("forbidden");
    expect(classifyReservationError(new ApiError(404, {})).kind).toBe("not-found");
    expect(classifyReservationError(new ApiError(401, {})).retryable).toBe(false);
  });

  it("surfaces the backend detail for a 409 conflict", () => {
    const failure = classifyReservationError(new ApiError(409, { detail: "Ticket type sold out" }));
    expect(failure).toEqual({ kind: "conflict", message: "Ticket type sold out", retryable: false });
  });

  it("uses a safe generic message for a 409 without useful detail", () => {
    const failure = classifyReservationError(new ApiError(409, {}));
    expect(failure.message).toBe("This ticket is no longer available to book.");
  });

  it("treats server and network errors as retryable", () => {
    expect(classifyReservationError(new ApiError(500, {}))).toMatchObject({ kind: "uncertain", retryable: true });
    expect(classifyReservationError(new Error("network down"))).toMatchObject({ kind: "uncertain", retryable: true });
  });
});

describe("booking order helpers", () => {
  it("formats booking amounts as Free or SGD", () => {
    expect(formatBookingAmount(0)).toBe("Free");
    expect(formatBookingAmount(1250)).toBe("S$12.50");
  });

  it("parses zero-based booking pagination defensively", () => {
    expect(readBookingPage(new URLSearchParams("page=2"))).toBe(2);
    expect(readBookingPage(new URLSearchParams(""))).toBe(0);
    expect(readBookingPage(new URLSearchParams("page=abc"))).toBe(0);
    expect(readBookingPage(new URLSearchParams("page=-1"))).toBe(0);
  });

  it("builds booking API paths", () => {
    expect(bookingsPath(0, 10)).toBe("/api/v1/bookings?page=0&size=10");
    expect(bookingPath("b1")).toBe("/api/v1/bookings/b1");
    expect(bookingPath("b/1")).toBe("/api/v1/bookings/b%2F1");
    expect(cancelBookingPath("b1")).toBe("/api/v1/bookings/b1/cancel");
  });

  it("maps booking load errors without leaking ownership", () => {
    expect(bookingError(new ApiError(404, { detail: "not found" }))).toBe("This booking is unavailable or does not belong to your account.");
    expect(bookingError(new ApiError(401, {}))).toContain("session has expired");
    expect(bookingError(new ApiError(403, {}))).toContain("attendee role");
    expect(bookingError(new Error("offline"))).toContain("We couldn't load bookings");
  });

  it("maps cancellation errors distinctly from load errors", () => {
    expect(cancelBookingError(new ApiError(409, { detail: "already started" }))).toBe("already started");
    expect(cancelBookingError(new ApiError(500, {}))).toContain("couldn't confirm your cancellation");
    expect(cancelBookingError(new Error("offline"))).toContain("couldn't confirm your cancellation");
  });
});
