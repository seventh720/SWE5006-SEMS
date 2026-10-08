// @vitest-environment jsdom
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ProfilePage } from "./ProfilePage";

vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ token: "profile-token", logout: vi.fn() }) }));
const fetchMock = vi.fn<typeof fetch>();
function response(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status }); }
function mount() { render(<MemoryRouter><ProfilePage /></MemoryRouter>); }
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal("fetch", (url: RequestInfo | URL, options?: RequestInit) => String(url) === "/api/v1/profile/avatar" ? Promise.resolve(response({ dataUrl: null })) : fetchMock(url, options)); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it("loads private details, saves changes, and allows clearing saved fields", async () => {
  fetchMock.mockImplementation(async (_url, options) => options?.method === "PUT"
    ? response(JSON.parse(options.body as string)) : response({ realName: "Alice", passportNumber: "P123" }));
  mount();
  await screen.findByLabelText("Real name");
  expect((screen.getByLabelText("Passport number") as HTMLInputElement).value).toBe("P123");
  expect(screen.getByText(/Your saved details are private to your account/)).toBeTruthy();
  fireEvent.change(screen.getByLabelText("Student ID number"), { target: { value: "S456" } });
  fireEvent.click(screen.getByRole("button", { name: "Save details" }));
  await screen.findByText("Saved details updated. Existing bookings are unchanged.");
  const saved = fetchMock.mock.calls.find(([, options]) => options?.method === "PUT")!;
  expect(saved[0]).toBe("/api/v1/profile");
  expect(saved[1]?.headers).toMatchObject({ Authorization: "Bearer profile-token" });
  expect(JSON.parse(saved[1]?.body as string)).toMatchObject({ studentId: "S456", realName: "Alice" });
  fireEvent.click(screen.getByRole("button", { name: "Clear fields" }));
  expect((screen.getByLabelText("Passport number") as HTMLInputElement).value).toBe("");
  fireEvent.click(screen.getByRole("button", { name: "Save details" }));
  await screen.findByText("Saved details updated. Existing bookings are unchanged.");
  expect(fetchMock.mock.calls.at(-1)?.[1]?.body).toBe("{}");
});

it("preserves entered details after a failed save", async () => {
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response({ detail: "Please retry" }, 500));
  mount();
  fireEvent.change(await screen.findByLabelText("Real name"), { target: { value: "Keep this name" } });
  fireEvent.click(screen.getByRole("button", { name: "Save details" }));
  await screen.findByRole("alert");
  expect((screen.getByLabelText("Real name") as HTMLInputElement).value).toBe("Keep this name");
});

it("prevents overwriting an unread profile when loading fails", async () => {
  fetchMock.mockRejectedValueOnce(new Error("offline"));
  mount();
  await screen.findByRole("alert");
  expect((screen.getByRole("button", { name: "Save details" }) as HTMLButtonElement).disabled).toBe(true);
  expect(screen.getByRole("button", { name: "Try again" })).toBeTruthy();
});
