import { ApiError } from "../../shared/api/client";
import { describe, expect, it } from "vitest";
import { fromSingaporeInput, toSingaporeInput, validSingaporeInput, managementError } from "./drafts";

describe("Singapore event form times", () => {
  it("converts Singapore midnight to the previous UTC date", () => {
    expect(fromSingaporeInput("2030-01-01T00:00")).toBe("2029-12-31T16:00:00.000Z");
  });
  it("restores saved instants without relying on the browser time zone", () => {
    expect(toSingaporeInput("2029-12-31T16:00:30Z")).toBe("2030-01-01T00:00");
    expect(fromSingaporeInput(toSingaporeInput("2029-12-31T16:00:30Z"))).toBe("2029-12-31T16:00:00.000Z");
  });
});

it("rejects invalid calendar dates and accepts minute precision", () => {
  expect(validSingaporeInput("2030-02-30T10:00")).toBe(false);
  expect(validSingaporeInput("2030-01-01T25:00")).toBe(false);
  expect(validSingaporeInput("2030-01-01T10:00:30")).toBe(false);
  expect(validSingaporeInput("2032-02-29T10:30")).toBe(true);
});


it("preserves the server reason instead of treating every conflict as a stale version", () => {
  for (const detail of ["Only drafts can be edited", "Booking requirements cannot change after publication", "This record was changed by another request. Reload before editing."]) {
    expect(managementError(new ApiError(409, { detail }))).toBe(detail);
  }
});
