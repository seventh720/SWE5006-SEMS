// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { StaffPage, StaffRoute } from "./StaffPage";

const auth = vi.hoisted(() => ({ user: { roles: ["STAFF"] } }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));
afterEach(cleanup);

function mount() {
  render(<MemoryRouter initialEntries={["/staff"]}><Routes>
    <Route path="/" element={<h1>Dashboard</h1>} />
    <Route element={<StaffRoute />}><Route path="/staff" element={<StaffPage />} /></Route>
  </Routes></MemoryRouter>);
}

it("keeps verification unavailable until assignments and check-in are connected", () => {
  auth.user.roles = ["STAFF"];
  mount();
  expect(screen.getByText("Event assignments are not available yet.")).toBeTruthy();
  expect((screen.getByRole("button", { name: "Open scanner" }) as HTMLButtonElement).disabled).toBe(true);
  expect((screen.getByRole("button", { name: "Verify ticket" }) as HTMLButtonElement).disabled).toBe(true);
  expect((screen.getByLabelText("Or enter a ticket code") as HTMLInputElement).disabled).toBe(true);
});

it.each(["ATTENDEE", "ORGANIZER", "ADMIN"])("does not grant %s staff access without the STAFF role", (role) => {
  auth.user.roles = [role];
  mount();
  expect(screen.getByRole("heading", { name: "Dashboard" })).toBeTruthy();
  expect(screen.queryByRole("heading", { name: "Staff workspace" })).toBeNull();
});
