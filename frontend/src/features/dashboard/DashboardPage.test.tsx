// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { DashboardPage } from "./DashboardPage";
const auth = vi.hoisted(() => ({ token: "token", user: { id: "u1", username: "Alice", email: "alice@example.test", status: "ACTIVE", roles: ["ATTENDEE"] }, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));
const fetchMock = vi.fn<typeof fetch>();
function response(data: unknown, status = 200) { return new Response(JSON.stringify(data), { status }); }
function mount() { render(<MemoryRouter><DashboardPage /></MemoryRouter>); }
beforeEach(() => { auth.user.roles = ["ATTENDEE"]; fetchMock.mockReset(); auth.logout.mockClear(); vi.stubGlobal("fetch", fetchMock); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it("shows real public events without RBAC demos or management entry points", async () => {
  fetchMock.mockResolvedValue(response({ items: [{ id: "e1", title: "Community day", startsAt: "2030-01-01T00:00:00Z", location: "Singapore" }] }));
  mount();
  expect((await screen.findByRole("link", { name: "Community day" })).getAttribute("href")).toBe("/events/e1");
  expect(screen.queryByText(/RBAC/)).toBeNull();
  expect(screen.queryByRole("button", { name: "Organizer" })).toBeNull();
  expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/events?page=0&size=4");
});
it("shows organizer totals from the summary and can switch to personal browsing", async () => {
  auth.user.roles = ["ATTENDEE", "ORGANIZER"];
  fetchMock.mockImplementation(async (url) => response(String(url).endsWith("summary") ? { drafts: 12, published: 3, cancelled: 2, upcoming: [] } : { items: [] }));
  mount();
  await screen.findByText("12");
  expect(screen.getByRole("link", { name: "Create event" }).getAttribute("href")).toBe("/organizer/events/new");
  expect(fetchMock.mock.calls[0][1]?.headers).toMatchObject({ Authorization: "Bearer token" });
  fireEvent.click(screen.getByRole("button", { name: "Personal" }));
  await screen.findByText("New experiences are on the way");
  expect(screen.queryByText("Drafts")).toBeNull();
});
it("shows honest staff availability without pretending assignments have been queried", () => {
  auth.user.roles = ["STAFF"]; mount();
  expect(screen.getByText(/Activity assignments, check-in/)).toBeTruthy();
  expect(screen.queryByText("0")).toBeNull();
  expect(fetchMock).not.toHaveBeenCalled();
});
it("defaults administrators to account management", async () => {
  auth.user.roles = ["ADMIN"];
  fetchMock.mockResolvedValue(response([{ roles: ["ORGANIZER", "STAFF"] }, { roles: ["ATTENDEE"] }]));
  mount(); await screen.findByText("Registered accounts");
  expect(screen.getByText("2")).toBeTruthy();
  expect(screen.getByRole("link", { name: "Manage users and roles →" }).getAttribute("href")).toBe("/admin/users");
  expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/admin/users");
});
it("retries failures instead of showing fake empty results", async () => {
  fetchMock.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(response({ items: [] }));
  mount(); fireEvent.click(await screen.findByRole("button", { name: "Try again" }));
  await screen.findByText("New experiences are on the way");
  expect(fetchMock).toHaveBeenCalledTimes(2);
});
it("offers reauthentication for expired management requests", async () => {
  auth.user.roles = ["ORGANIZER"]; fetchMock.mockResolvedValue(response({}, 401)); mount();
  fireEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
  expect(auth.logout).toHaveBeenCalledOnce();
});
