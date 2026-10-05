import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api/client";
import { classifyReservationError, newIdempotencyKey, validateBookingQuantity } from "./bookings";

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
