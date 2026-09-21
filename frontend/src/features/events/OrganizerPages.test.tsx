// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { EditEventPage, MyEventsPage, NewEventPage, OrganizerRoute } from "./OrganizerPages";

const auth = vi.hoisted(() => ({ token: "test-token", user: { roles: ["ORGANIZER"] }, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));
const fetchMock = vi.fn<typeof fetch>();
const draft = { id: "event-1", title: "My draft", description: "A community event", location: "Room A",
  startsAt: "2030-01-01T02:00:00Z", endsAt: "2030-01-01T04:00:00Z", capacity: 100, status: "DRAFT", version: 0,
  createdAt: "2029-12-01T00:00:00Z", updatedAt: "2029-12-01T00:00:00Z" };
function response(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }); }
function mount(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/" element={<p>Dashboard</p>} /><Route path="/login" element={<p>Sign-in page</p>} />
    <Route element={<OrganizerRoute />}>
      <Route path="/organizer/events" element={<MyEventsPage />} />
      <Route path="/organizer/events/new" element={<NewEventPage />} />
      <Route path="/organizer/events/:id" element={<EditEventPage />} />
    </Route>
  </Routes></MemoryRouter>);
}
beforeEach(() => { auth.user.roles = ["ORGANIZER"]; auth.logout.mockClear(); fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("organizer draft flows", () => {
  it("saves a new draft with Singapore times and shows the persisted result", async () => {
    fetchMock.mockImplementation(async (_url, options) => options?.method === "POST" ? response(draft, 201) : response(draft));
    mount("/organizer/events/new");
    fireEvent.change(screen.getByLabelText("Event title"), { target: { value: "My draft" } });
    fireEvent.change(screen.getByLabelText("Location"), { target: { value: "Room A" } });
    fireEvent.change(screen.getByLabelText("Description"), { target: { value: "A community event" } });
    fireEvent.change(screen.getByLabelText("Start time"), { target: { value: "2030-01-01T10:00" } });
    fireEvent.change(screen.getByLabelText("End time"), { target: { value: "2030-01-01T12:00" } });
    fireEvent.change(screen.getByLabelText("Capacity"), { target: { value: "100" } });
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }));
    await screen.findByText("Draft saved successfully.");
    const [, options] = fetchMock.mock.calls.find(([, options]) => options?.method === "POST")!;
    const body = JSON.parse(options?.body as string);
    expect(body.startsAt).toBe("2030-01-01T02:00:00.000Z");
    expect(body.capacity).toBe(100);
    expect(body).not.toHaveProperty("organizerId");
    expect(options?.headers).toMatchObject({ Authorization: "Bearer test-token" });
    expect((screen.getByLabelText("Event title") as HTMLInputElement).value).toBe("My draft");
  });

  it("preserves edits on conflict, then reloads the new version", async () => {
    let current = { ...draft };
    fetchMock.mockImplementation(async (_url, options) => {
      if (options?.method === "PUT") { current = { ...draft, title: "Saved elsewhere", version: 1 }; return response({ detail: "Conflict" }, 409); }
      return response(current);
    });
    mount("/organizer/events/event-1");
    await screen.findByLabelText("Event title");
    fireEvent.change(screen.getByLabelText("Event title"), { target: { value: "Unsaved text" } });
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }));
    await screen.findByText(/Copy any unsaved text/);
    expect((screen.getByLabelText("Event title") as HTMLInputElement).value).toBe("Unsaved text");
    expect((screen.getByRole("button", { name: "Save draft" }) as HTMLButtonElement).disabled).toBe(true);
    const put = fetchMock.mock.calls.find(([, options]) => options?.method === "PUT")!;
    expect(JSON.parse(put[1]?.body as string).version).toBe(0);
    fireEvent.click(screen.getByRole("button", { name: "Reload latest version" }));
    await waitFor(() => expect((screen.getByLabelText("Event title") as HTMLInputElement).value).toBe("Saved elsewhere"));
  });

  it("rejects reversed times before making a save request", async () => {
    fetchMock.mockResolvedValue(response(draft));
    mount("/organizer/events/event-1");
    await screen.findByLabelText("End time");
    fireEvent.change(screen.getByLabelText("End time"), { target: { value: "2029-01-01T10:00" } });
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }));
    expect(screen.getByText("End time must be after start time.")).toBeTruthy();
    expect(fetchMock.mock.calls.some(([, options]) => options?.method === "PUT")).toBe(false);
  });

  it("provides a sign-in path when the token expires", async () => {
    fetchMock.mockResolvedValue(response({}, 401));
    mount("/organizer/events");
    fireEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    expect(auth.logout).toHaveBeenCalledOnce();
    expect(screen.getByText("Sign-in page")).toBeTruthy();
  });

  it("keeps published events read-only", async () => {
    fetchMock.mockResolvedValue(response({ ...draft, status: "PUBLISHED" }));
    mount("/organizer/events/event-1");
    await screen.findByText("Only draft events can be edited.");
    expect(screen.queryByRole("button", { name: "Save draft" })).toBeNull();
    expect(screen.getByLabelText("Event title").closest("fieldset")?.disabled).toBe(true);
  });

  it("prevents attendees from entering the organizer routes", () => {
    auth.user.roles = ["ATTENDEE"];
    mount("/organizer/events/new");
    expect(screen.getByText("Dashboard")).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("confirms publication and cancellation and refreshes action availability", async () => {
    let current = { ...draft };
    fetchMock.mockImplementation(async (url, options) => {
      if (options?.method === "POST") {
        current = { ...current, status: String(url).endsWith("/publish") ? "PUBLISHED" : "CANCELLED", version: current.version + 1 };
      }
      return response(current);
    });
    mount("/organizer/events/event-1");
    fireEvent.click(await screen.findByRole("button", { name: "Publish event" }));
    expect(fetchMock.mock.calls.some(([, options]) => options?.method === "POST")).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: "Confirm publication" }));
    await screen.findByText("Event published. It is now visible to everyone.");
    expect(screen.queryByRole("button", { name: "Publish event" })).toBeNull();
    expect(screen.getByRole("link", { name: "View public event →" }).getAttribute("href")).toBe("/events/event-1");
    fireEvent.click(screen.getByRole("button", { name: "Cancel event" }));
    fireEvent.click(screen.getByRole("button", { name: "Go back" }));
    expect(fetchMock.mock.calls.filter(([, options]) => options?.method === "POST")).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Cancel event" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm cancellation" }));
    await screen.findByText("This event is cancelled and cannot be restored.");
    expect(screen.queryByRole("button", { name: "Cancel event" })).toBeNull();
    expect(screen.queryByRole("link", { name: "View public event →" })).toBeNull();
    const posts = fetchMock.mock.calls.filter(([, options]) => options?.method === "POST");
    expect(JSON.parse(posts[0][1]?.body as string).version).toBe(0);
    expect(JSON.parse(posts[1][1]?.body as string).version).toBe(1);
  });

  it("requires saving changed fields before publishing", async () => {
    fetchMock.mockResolvedValue(response(draft));
    mount("/organizer/events/event-1");
    await screen.findByLabelText("Event title");
    fireEvent.change(screen.getByLabelText("Event title"), { target: { value: "Unsaved title" } });
    expect((screen.getByRole("button", { name: "Publish event" }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText("Save your changes before publishing or cancelling.")).toBeTruthy();
  });

});
