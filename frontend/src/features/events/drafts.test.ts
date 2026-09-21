import { describe, expect, it } from "vitest";
import { fromSingaporeInput, toSingaporeInput } from "./drafts";

describe("Singapore event form times", () => {
  it("converts Singapore midnight to the previous UTC date", () => {
    expect(fromSingaporeInput("2030-01-01T00:00")).toBe("2029-12-31T16:00:00.000Z");
  });
  it("restores saved instants without relying on the browser time zone", () => {
    expect(toSingaporeInput("2029-12-31T16:00:30Z")).toBe("2030-01-01T00:00:30");
    expect(fromSingaporeInput(toSingaporeInput("2029-12-31T16:00:30Z"))).toBe("2029-12-31T16:00:30.000Z");
  });
});
