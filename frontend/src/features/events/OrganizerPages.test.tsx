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
    fireEvent.click(screen.getByRole("combobox", { name: "Card illustration" }));
    fireEvent.click(screen.getByRole("option", { name: "Technology" }));
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }));
    await screen.findByText("Draft saved successfully.");
    const [, options] = fetchMock.mock.calls.find(([, options]) => options?.method === "POST")!;
    const body = JSON.parse(options?.body as string);
    expect(body.startsAt).toBe("2030-01-01T02:00:00.000Z");
    expect(body.capacity).toBe(100);
    expect(body.illustration).toBe("TECH");
    expect(body.registrationOpensAt).toBeNull();
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
    await screen.findByText("Conflict");
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

  it("allows published details to change while keeping booking requirements fixed", async () => {
    fetchMock.mockResolvedValue(response({ ...draft, status: "PUBLISHED" }));
    mount("/organizer/events/event-1");
    await screen.findByRole("button", { name: "Save changes" });
    expect(screen.queryByRole("button", { name: "Save draft" })).toBeNull();
    expect(screen.getByLabelText("Event title").closest("fieldset")?.disabled).toBe(false);
    expect(screen.getByLabelText("Real name").closest("fieldset")?.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText("Location"), { target: { value: "New venue" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([, options]) => options?.method === "PUT")).toBe(true));
    const update = fetchMock.mock.calls.find(([, options]) => options?.method === "PUT")!;
    expect(JSON.parse(update[1]?.body as string).location).toBe("New venue");
  });

  it("locks an elapsed opening and preserves its original seconds when saving other details", async () => {
    const opening = "2020-01-01T02:00:37Z";
    fetchMock.mockResolvedValue(response({ ...draft, status: "PUBLISHED", registrationOpensAt: opening }));
    mount("/organizer/events/event-1");
    const field = await screen.findByLabelText("Registration opens", { exact: false });
    expect((field as HTMLInputElement).readOnly).toBe(true);
    fireEvent.change(screen.getByLabelText("Location"), { target: { value: "New venue" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([, options]) => options?.method === "PUT")).toBe(true));
    const update = fetchMock.mock.calls.find(([, options]) => options?.method === "PUT")!;
    expect(JSON.parse(update[1]?.body as string).registrationOpensAt).toBe(opening);
  });

  it("keeps a future opening editable", async () => {
    fetchMock.mockResolvedValue(response({ ...draft, status: "PUBLISHED", registrationOpensAt: "2099-01-01T00:00:00Z" }));
    mount("/organizer/events/event-1");
    const field = await screen.findByLabelText("Registration opens");
    expect((field as HTMLInputElement).readOnly).toBe(false);
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
    expect(screen.getByText("Save your changes before using event actions.")).toBeTruthy();
  });

});

it("saves booking requirements and prevents publication while they are unsaved", async () => {
  let current = { ...draft, bookingRequirements: { realName: false, email: false, phone: false, studentId: false, passport: false, customFieldLabel: null } };
  fetchMock.mockImplementation(async (_url, options) => {
    if (options?.method === "PUT") current = { ...current, ...JSON.parse(options.body as string), version: 1 };
    return response(current);
  });
  mount("/organizer/events/event-1");
  fireEvent.click(await screen.findByLabelText("Real name"));
  fireEvent.click(screen.getByLabelText("Student ID number"));
  expect((screen.getByRole("button", { name: "Publish event" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Save draft" }));
  await screen.findByText("Draft saved successfully.");
  const update = fetchMock.mock.calls.find(([, options]) => options?.method === "PUT")!;
  expect(JSON.parse(update[1]?.body as string).bookingRequirements).toEqual({ realName: true, email: false, phone: false, studentId: true, passport: false, customFieldLabel: null });
  expect((screen.getByLabelText("Real name") as HTMLInputElement).checked).toBe(true);
});

it("copies a cancelled event into a separate editable draft", async () => {
  const copy = { ...draft, id: "new-draft", bookingRequirements: { realName: true, email: false, phone: false, studentId: true, passport: false, customFieldLabel: "Department" } };
  fetchMock.mockImplementation(async (url, options) => {
    if (options?.method === "POST") return response(copy, 201);
    return response(String(url).endsWith("/new-draft") ? copy : { ...draft, status: "CANCELLED" });
  });
  mount("/organizer/events/event-1");
  fireEvent.click(await screen.findByRole("button", { name: "Copy to new draft" }));
  await screen.findByText(/Event copied to a new draft/);
  expect((screen.getByLabelText("Event title") as HTMLInputElement).value).toBe(draft.title);
  expect(screen.getByRole("button", { name: "Save draft" })).toBeTruthy();
  expect((screen.getByLabelText("Information to request") as HTMLInputElement).value).toBe("Department");
  expect(fetchMock.mock.calls.find(([, options]) => options?.method === "POST")?.[0]).toBe("/api/v1/organizer/events/event-1/copy");
});

it("keeps event details and requirements after a publication failure", async () => {
  fetchMock.mockImplementation(async (_url, options) => options?.method === "POST"
    ? response({ detail: "Temporary failure" }, 500) : response(draft));
  mount("/organizer/events/event-1");
  fireEvent.click(await screen.findByRole("button", { name: "Publish event" }));
  fireEvent.click(screen.getByRole("button", { name: "Confirm publication" }));
  await screen.findByText(/couldn't complete the request/);
  expect((screen.getByLabelText("Event title") as HTMLInputElement).value).toBe(draft.title);
  expect((screen.getByRole("button", { name: "Publish event" }) as HTMLButtonElement).disabled).toBe(false);
});

it("saves independent document requirements and an organizer-defined question", async () => {
  fetchMock.mockImplementation(async (_url, options) => options?.method === "PUT"
    ? response({ ...draft, ...JSON.parse(options.body as string), version: 1 }) : response(draft));
  mount("/organizer/events/event-1");
  fireEvent.click(await screen.findByLabelText("Student ID number"));
  fireEvent.click(screen.getByLabelText("Passport number"));
  fireEvent.click(screen.getByLabelText("Custom information"));
  fireEvent.change(screen.getByLabelText("Information to request"), { target: { value: "Dietary requirements" } });
  fireEvent.click(screen.getByRole("button", { name: "Save draft" }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([, options]) => options?.method === "PUT")).toBe(true));
  const update = fetchMock.mock.calls.find(([, options]) => options?.method === "PUT")!;
  expect(JSON.parse(update[1]?.body as string).bookingRequirements).toMatchObject({ studentId: true, passport: true, customFieldLabel: "Dietary requirements" });
});

it("uses English minute-only fields and saves the Singapore registration deadline", async () => {
  fetchMock.mockResolvedValue(response(draft));
  mount("/organizer/events/event-1");
  const start = await screen.findByLabelText("Start time") as HTMLInputElement;
  expect(start.type).toBe("text");
  expect(start.placeholder).toBe("YYYY-MM-DD HH:mm");
  expect(start.value).toBe("2030-01-01 10:00");
  fireEvent.change(screen.getByLabelText("Registration opens"), { target: { value: "2030-01-01 08:00" } });
  fireEvent.change(screen.getByLabelText("Registration deadline"), { target: { value: "2030-01-01 09:30" } });
  fireEvent.click(screen.getByRole("button", { name: "Save draft" }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([, options]) => options?.method === "PUT")).toBe(true));
  const update = fetchMock.mock.calls.find(([, options]) => options?.method === "PUT")!;
  expect(JSON.parse(update[1]?.body as string).registrationClosesAt).toBe("2030-01-01T01:30:00.000Z");
  expect(JSON.parse(update[1]?.body as string).registrationOpensAt).toBe("2030-01-01T00:00:00.000Z");
});

it("rejects a registration deadline after the start without saving", async () => {
  fetchMock.mockResolvedValue(response(draft));
  mount("/organizer/events/event-1");
  fireEvent.change(await screen.findByLabelText("Registration deadline"), { target: { value: "2030-01-01 11:00" } });
  fireEvent.click(screen.getByRole("button", { name: "Save draft" }));
  expect(await screen.findByText("Registration deadline must be on or before start time.")).toBeTruthy();
  expect(fetchMock.mock.calls.some(([, options]) => options?.method === "PUT")).toBe(false);
});

it("hides draft orders and creates a free ticket explicitly", async () => {
  fetchMock.mockImplementation(async (_url, options) => response(options?.method === "POST" ? { name: "Free admission", quota: 100, priceMinor: 0 } : draft));
  mount("/organizer/events/event-1");
  await screen.findByRole("button", { name: "Create free ticket" });
  expect(screen.queryByRole("link", { name: "View event orders →" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Create free ticket" }));
  await screen.findByText("Free admission is ready: SGD 0, 100 places. You can now publish the event.");
  expect(fetchMock.mock.calls.some(([url, options]) => url === "/api/v1/organizer/events/event-1/ticket-types/default-free" && options?.method === "POST")).toBe(true);
});
