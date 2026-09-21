import { describe, expect, it } from "vitest";
import { eventListPath, formatEventTime, readEventQuery } from "./events";

describe("event browsing query", () => {
  it("restores search and page from a shareable URL", () => {
    expect(readEventQuery(new URLSearchParams("page=2&keyword=%20Open%20Day%20")))
      .toEqual({ page: 2, keyword: "Open Day" });
  });

  it.each(["-1", "1.5", "nope", "Infinity", "9007199254740992"])("resets invalid page %s", (page) => {
    expect(readEventQuery(new URLSearchParams({ page })).page).toBe(0);
  });

  it("encodes special characters without injecting query parameters", () => {
    const url = new URL(eventListPath(0, "Art & Music + Live"), "https://example.test");
    expect(url.searchParams.get("keyword")).toBe("Art & Music + Live");
    expect(url.searchParams.get("size")).toBe("10");
    expect([...url.searchParams.keys()]).toEqual(["page", "size", "keyword"]);
  });

  it("omits an empty keyword", () => {
    expect(eventListPath(0, "  ")).toBe("/api/v1/events?page=0&size=10");
  });

  it("shows the same Singapore time for equivalent instants", () => {
    expect(formatEventTime("2026-10-01T02:00:00Z")).toBe(formatEventTime("2026-10-01T10:00:00+08:00"));
    expect(formatEventTime("2026-10-01T02:00:00Z")).toContain("10:00");
  });
});
