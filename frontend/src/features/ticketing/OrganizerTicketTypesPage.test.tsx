// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { OrganizerRoute } from "../events/OrganizerPages";
import { OrganizerTicketTypesPage } from "./OrganizerTicketTypesPage";

const auth = vi.hoisted(() => ({ token: "test-token", user: { roles: ["ORGANIZER"] }, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));
const fetchMock = vi.fn<typeof fetch>();
const free = { id: "t1", name: "General admission", priceMinor: 0, currency: "SGD", quota: 10, bookedQuantity: 0, version: 0 };
function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
function mount(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/" element={<p>Dashboard</p>} /><Route path="/login" element={<p>Sign-in page</p>} />
    <Route element={<OrganizerRoute />}>
      <Route path="/organizer/events/:id/ticket-types" element={<OrganizerTicketTypesPage />} />
    </Route>
  </Routes></MemoryRouter>);
}
beforeEach(() => { auth.user.roles = ["ORGANIZER"]; auth.logout.mockClear(); fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("organizer ticket type management", () => {
  it("lists existing ticket types and sends the Bearer token on load", async () => {
    fetchMock.mockResolvedValue(response([free]));
    mount("/organizer/events/event-1/ticket-types");
    expect(await screen.findByText("General admission")).toBeTruthy();
    expect(screen.getByText("1 ticket type")).toBeTruthy();
    const get = fetchMock.mock.calls.find(([, options]) => !options?.method || options.method === "GET")!;
    expect(get[1]?.headers).toMatchObject({ Authorization: "Bearer test-token" });
  });

  it("creates a ticket type with a minor-unit price and Bearer token", async () => {
    fetchMock.mockImplementation(async (_url, options) =>
      options?.method === "POST" ? response({ ...free, id: "t-new" }, 201) : response([]));
    mount("/organizer/events/event-1/ticket-types");
    await screen.findByText("No ticket types yet");
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Early bird" } });
    fireEvent.change(screen.getByLabelText("Price (SGD)"), { target: { value: "12.50" } });
    fireEvent.change(screen.getByLabelText("Quota"), { target: { value: "25" } });
    fireEvent.click(screen.getByRole("button", { name: "Create ticket type" }));
    await screen.findByText("Ticket type created.");
    const post = fetchMock.mock.calls.find(([, options]) => options?.method === "POST")!;
    expect(JSON.parse(post[1]?.body as string)).toEqual({ name: "Early bird", priceMinor: 1250, currency: "SGD", quota: 25 });
    expect(post[1]?.headers).toMatchObject({ Authorization: "Bearer test-token" });
  });

  it("rejects invalid price and quota before sending", async () => {
    fetchMock.mockResolvedValue(response([]));
    mount("/organizer/events/event-1/ticket-types");
    await screen.findByText("No ticket types yet");
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Bad" } });
    fireEvent.change(screen.getByLabelText("Price (SGD)"), { target: { value: "-5" } });
    fireEvent.change(screen.getByLabelText("Quota"), { target: { value: "0" } });
    fireEvent.click(screen.getByRole("button", { name: "Create ticket type" }));
    expect(screen.getByText("Enter a non-negative price in Singapore dollars, e.g. 12.50.")).toBeTruthy();
    expect(screen.getByText("Enter a positive whole number up to 2147483647.")).toBeTruthy();
    expect(fetchMock.mock.calls.some(([, options]) => options?.method === "POST")).toBe(false);
  });

  it("shows backend field validation errors", async () => {
    fetchMock.mockImplementation(async (_url, options) => options?.method === "POST"
      ? response({ title: "Invalid", fieldErrors: { name: "Name is too long", priceMinor: "Price must be non-negative", quota: "Total quota exceeds capacity" } }, 400)
      : response([]));
    mount("/organizer/events/event-1/ticket-types");
    await screen.findByText("No ticket types yet");
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "X" } });
    fireEvent.change(screen.getByLabelText("Price (SGD)"), { target: { value: "0" } });
    fireEvent.change(screen.getByLabelText("Quota"), { target: { value: "5" } });
    fireEvent.click(screen.getByRole("button", { name: "Create ticket type" }));
    expect(await screen.findByText("Name is too long")).toBeTruthy();
    expect(screen.getByText("Price must be non-negative")).toBeTruthy();
    expect(screen.getByText("Total quota exceeds capacity")).toBeTruthy();
  });

  it("keeps user input when a conflict is returned", async () => {
    fetchMock.mockImplementation(async (_url, options) =>
      options?.method === "PUT" ? response({ detail: "Conflict" }, 409) : response([free]));
    mount("/organizer/events/event-1/ticket-types");
    await screen.findByText("General admission");
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Unsaved name" } });
    fireEvent.click(screen.getByRole("button", { name: "Save ticket type" }));
    await screen.findByText(/has changed or can no longer be modified/);
    expect((screen.getByLabelText("Name") as HTMLInputElement).value).toBe("Unsaved name");
    const put = fetchMock.mock.calls.find(([, options]) => options?.method === "PUT")!;
    expect(JSON.parse(put[1]?.body as string).version).toBe(0);
  });

  it("prevents duplicate submissions while saving", async () => {
    let resolvePost!: (value: Response) => void;
    fetchMock.mockImplementation(async (_url, options) => {
      if (options?.method === "POST") return new Promise((resolve) => { resolvePost = resolve; });
      return response([]);
    });
    mount("/organizer/events/event-1/ticket-types");
    await screen.findByText("No ticket types yet");
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Early bird" } });
    fireEvent.change(screen.getByLabelText("Price (SGD)"), { target: { value: "0" } });
    fireEvent.change(screen.getByLabelText("Quota"), { target: { value: "5" } });
    fireEvent.click(screen.getByRole("button", { name: "Create ticket type" }));
    expect((screen.getByRole("button", { name: "Saving…" }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Saving…" }));
    resolvePost(response({ ...free, id: "t-new" }, 201));
    await screen.findByText("Ticket type created.");
    expect(fetchMock.mock.calls.filter(([, options]) => options?.method === "POST")).toHaveLength(1);
  });

  it("prevents attendees from entering the ticket type management route", () => {
    auth.user.roles = ["ATTENDEE"];
    mount("/organizer/events/event-1/ticket-types");
    expect(screen.getByText("Dashboard")).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("does not invent a version when the backend omits one", async () => {
    fetchMock.mockResolvedValue(response([{ id: "t1", name: "Legacy", priceMinor: 0, currency: "SGD", quota: 10, bookedQuantity: 0 }]));
    mount("/organizer/events/event-1/ticket-types");
    expect(await screen.findByText("Legacy")).toBeTruthy();
    expect(screen.getByText("Editing unavailable")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Edit" })).toBeNull();
    expect(fetchMock.mock.calls.some(([, options]) => options?.method === "PUT")).toBe(false);
  });

  it("provides a sign-in path when the token expires", async () => {
    fetchMock.mockResolvedValue(response({}, 401));
    mount("/organizer/events/event-1/ticket-types");
    fireEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    expect(auth.logout).toHaveBeenCalledOnce();
    expect(screen.getByText("Sign-in page")).toBeTruthy();
  });
});
