// @vitest-environment jsdom
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { EventListPage, EventDetailPage } from "./EventPages";
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ user: null }) }));
const fetchMock = vi.fn<typeof fetch>();
const event = { id: "e1", title: "Open Day", description: "Meet our community", location: "Singapore", startsAt: "2030-01-01T02:00:00Z", endsAt: "2030-01-01T04:00:00Z", capacity: 100, status: "PUBLISHED" };
function response(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status }); }
function mount(path = "/events") { render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/events" element={<EventListPage />} /><Route path="/events/:id" element={<EventDetailPage />} /></Routes></MemoryRouter>); }
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it("searches from page zero, paginates and preserves filters through detail navigation", async () => {
  fetchMock.mockImplementation(async (input) => {
    const url = new URL(String(input), "http://test");
    if (url.pathname.endsWith("/e1")) return response(event);
    return response({ items: [event], page: Number(url.searchParams.get("page")), totalPages: 2, totalElements: 11, size: 10 });
  });
  mount("/events?page=1"); await screen.findByText("Page 2 of 2");
  fireEvent.change(screen.getByLabelText("Search by event title"), { target: { value: " Open Day " } });
  fireEvent.click(screen.getByRole("button", { name: "Search events" })); await screen.findByText("Page 1 of 2");
  expect(String(fetchMock.mock.calls.at(-1)![0])).toContain("keyword=Open+Day");
  fireEvent.click(screen.getByRole("button", { name: "Next" })); await screen.findByText("Page 2 of 2");
  expect((screen.getByRole("button", { name: "Next" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole("link", { name: "View details →" })); await screen.findByText("About this event");
  fireEvent.click(screen.getByRole("link", { name: "← Back to events" })); await screen.findByText("Page 2 of 2");
  expect((screen.getByLabelText("Search by event title") as HTMLInputElement).value).toBe("Open Day");
});
it("retries network failures and clears searches with no results", async () => {
  fetchMock.mockRejectedValueOnce(new Error("offline")).mockImplementation(async () => response({ items: [], page: 0, totalPages: 0, totalElements: 0, size: 10 }));
  mount("/events?keyword=Missing"); fireEvent.click(await screen.findByRole("button", { name: "Try again" }));
  await screen.findByText("No matching events"); fireEvent.click(screen.getByRole("button", { name: "Clear search" }));
  await screen.findByText("More experiences are on the way");
  expect(String(fetchMock.mock.calls.at(-1)![0])).not.toContain("keyword");
});
it("does not display hidden event information on a 404", async () => {
  fetchMock.mockResolvedValue(response({ detail: "Event not found" }, 404)); mount("/events/hidden");
  await screen.findByText("This event is unavailable. It may no longer be published.");
  expect(screen.queryByText("About this event")).toBeNull();
});
it("ignores a late old response after a new search", async () => {
  let resolveOld!: (response: Response) => void;
  fetchMock.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve; }))
    .mockResolvedValueOnce(response({ items: [], page: 0, totalPages: 0, totalElements: 0, size: 10 }));
  mount(); fireEvent.change(screen.getByLabelText("Search by event title"), { target: { value: "Missing" } });
  fireEvent.click(screen.getByRole("button", { name: "Search events" })); await screen.findByText("No matching events");
  await act(async () => { resolveOld(response({ items: [event], totalElements: 1, totalPages: 1, page: 0, size: 10 })); });
  expect(screen.queryByRole("link", { name: "Open Day" })).toBeNull();
});
