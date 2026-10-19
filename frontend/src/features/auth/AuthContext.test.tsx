// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { AuthProvider, useAuth } from "./AuthContext";
import { apiRequest } from "../../shared/api/client";
import { signalSessionChange } from "../../shared/auth/sessionEvents";

const user = { id: "u1", username: "alice", email: "alice@example.com", status: "ACTIVE", roles: ["ATTENDEE"] };
const fetchMock = vi.fn<typeof fetch>();
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
function Probe() {
  const auth = useAuth();
  return <><p>{auth.user ? "Authenticated" : "Signed out"}</p><p>{auth.sessionNotice}</p>
    <button onClick={() => void auth.login(user.email, "Password123")}>Login</button></>;
}
function store(expiresAt = Date.now() + 3_600_000) {
  sessionStorage.setItem("sems-auth", JSON.stringify({ token: "old-token", user, expiresAt }));
}
async function mount() {
  render(<AuthProvider><Probe /></AuthProvider>);
  await act(async () => {});
}
beforeEach(() => {
  sessionStorage.clear(); vi.useFakeTimers(); vi.setSystemTime(new Date("2026-10-20T00:00:00Z"));
  fetchMock.mockReset(); fetchMock.mockImplementation(async () => json(user)); vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); sessionStorage.clear(); });

it("does not restore an expired cached session", async () => {
  store(Date.now() - 1); await mount();
  expect(screen.getByText("Signed out")).toBeTruthy();
  expect(screen.getByText(/session has expired/)).toBeTruthy();
  expect(fetchMock).not.toHaveBeenCalled();
});
it("stores the server's one-hour lifetime and signs out at expiry", async () => {
  fetchMock.mockImplementation(async url => json(url === "/api/v1/auth/login"
    ? { accessToken: "new-token", expiresIn: 3600, user } : user));
  await mount(); await act(async () => { fireEvent.click(screen.getByText("Login")); });
  expect(JSON.parse(sessionStorage.getItem("sems-auth")!).expiresAt).toBe(Date.now() + 3_600_000);
  await act(async () => { await vi.advanceTimersByTimeAsync(3_599_999); });
  expect(screen.getByText("Authenticated")).toBeTruthy();
  await act(async () => { await vi.advanceTimersByTimeAsync(1); });
  expect(screen.getByText("Signed out")).toBeTruthy();
  expect(sessionStorage.getItem("sems-auth")).toBeNull();
});
it("detects changed roles on polling and explains why reauthentication is needed", async () => {
  store(); await mount();
  fetchMock.mockImplementation(async () => json({ ...user, roles: ["ATTENDEE", "STAFF"] }));
  await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
  expect(screen.getByText("Signed out")).toBeTruthy();
  expect(screen.getByText(/account roles have changed/)).toBeTruthy();
});
it("keeps a valid session for a resource-level 403 when roles are unchanged", async () => {
  store(); await mount();
  fetchMock.mockImplementation(async url => url === "/api/v1/auth/me" ? json(user) : json({}, 403));
  await act(async () => { await apiRequest("/api/v1/private-resource", {}, "old-token").catch(() => {}); });
  expect(screen.getByText("Authenticated")).toBeTruthy();
});
it("clears the active session on an API 401 but ignores an older token's response", async () => {
  store(); await mount();
  await act(async () => { signalSessionChange({ token: "unrelated-old-token", reason: "expired" }); });
  expect(screen.getByText("Authenticated")).toBeTruthy();
  fetchMock.mockImplementation(async () => json({}, 401));
  await act(async () => { await apiRequest("/api/v1/private-resource", {}, "old-token").catch(() => {}); });
  expect(screen.getByText("Signed out")).toBeTruthy();
});
it("rechecks expiry when returning from a suspended browser tab", async () => {
  store(); await mount(); vi.setSystemTime(Date.now() + 3_600_001);
  await act(async () => { window.dispatchEvent(new Event("focus")); });
  expect(screen.getByText("Signed out")).toBeTruthy();
});
it("does not sign out because of a temporary network failure", async () => {
  store(); fetchMock.mockRejectedValue(new TypeError("Network unavailable")); await mount();
  expect(screen.getByText("Authenticated")).toBeTruthy();
});
it("never restores a session after an obsolete role check completes", async () => {
  store(); let resolve!: (value: Response) => void;
  fetchMock.mockImplementation(() => new Promise<Response>(done => { resolve = done; }));
  await mount();
  await act(async () => { signalSessionChange({ token: "old-token", reason: "roles" }); });
  await act(async () => { resolve(json(user)); });
  expect(screen.getByText("Signed out")).toBeTruthy();
});
