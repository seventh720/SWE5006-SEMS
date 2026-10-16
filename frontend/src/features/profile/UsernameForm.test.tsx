// @vitest-environment jsdom
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { AuthProvider, useAuth } from "../auth/AuthContext";
import { UsernameForm } from "./UsernameForm";

const fetchMock = vi.fn<typeof fetch>();
function CurrentUsername() { return <p>Welcome, {useAuth().user?.username}</p>; }
function mount() { return render(<AuthProvider><CurrentUsername /><UsernameForm /></AuthProvider>); }
beforeEach(() => {
  sessionStorage.setItem("sems-auth", JSON.stringify({ token: "original-token", user: {
    id: "u1", username: "alice", email: "alice@example.com", roles: ["ATTENDEE"], status: "ACTIVE",
  } }));
  fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => { cleanup(); sessionStorage.clear(); vi.unstubAllGlobals(); });

it("updates the shared username immediately and retains it across reloads", async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ username: "new.name" }), { status: 200 }));
  const view = mount();
  fireEvent.change(screen.getByLabelText("Username"), { target: { value: "New.Name" } });
  fireEvent.click(screen.getByRole("button", { name: "Save username" }));
  await screen.findByText("Welcome, new.name");
  expect(JSON.parse(sessionStorage.getItem("sems-auth")!).user).toMatchObject({ username: "new.name", email: "alice@example.com", roles: ["ATTENDEE"] });
  expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/profile/username");
  expect(fetchMock.mock.calls[0][1]?.headers).toMatchObject({ Authorization: "Bearer original-token" });
  view.unmount(); mount();
  expect((screen.getByLabelText("Username") as HTMLInputElement).value).toBe("new.name");
});

it("keeps the entered name and existing identity when the name is taken", async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ detail: "Username is already registered" }), { status: 409 }));
  mount();
  fireEvent.change(screen.getByLabelText("Username"), { target: { value: "taken" } });
  fireEvent.click(screen.getByRole("button", { name: "Save username" }));
  await screen.findByText("Username is already registered");
  expect((screen.getByLabelText("Username") as HTMLInputElement).value).toBe("taken");
  expect(screen.getByText("Welcome, alice")).toBeTruthy();
});

it("offers reauthentication after the session expires", async () => {
  fetchMock.mockResolvedValue(new Response("{}", { status: 401 }));
  mount();
  fireEvent.change(screen.getByLabelText("Username"), { target: { value: "new-name" } });
  fireEvent.click(screen.getByRole("button", { name: "Save username" }));
  fireEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
  expect(sessionStorage.getItem("sems-auth")).toBeNull();
});
