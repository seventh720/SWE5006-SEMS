// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { OrganizerStaffPage } from "./OrganizerStaffPage";
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({token: "token", logout: vi.fn()}) }));
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
const member = { id: "staff1", username: "carrot", eligible: true, assigned: false };
const response = (assigned = false) => new Response(JSON.stringify({eventTitle: "Workshop", staff: [{...member, assigned}]}), {status: 200});
function mount() { render(<MemoryRouter initialEntries={["/organizer/events/event1/staff"]}><Routes><Route path="/organizer/events/:id/staff" element={<OrganizerStaffPage />} /></Routes></MemoryRouter>); }
it("assigns and removes staff, reloading the confirmed server state", async () => {
 const fetch = vi.fn().mockResolvedValueOnce(response()).mockResolvedValueOnce(new Response(null,{status:204})).mockResolvedValueOnce(response(true)).mockResolvedValueOnce(new Response(null,{status:204})).mockResolvedValueOnce(response());
 vi.stubGlobal("fetch",fetch); mount();
 fireEvent.click(await screen.findByRole("combobox",{name:"Staff member"}));
 fireEvent.click(screen.getByRole("option",{name:"carrot"}));
 fireEvent.click(screen.getByRole("button",{name:"Assign staff"}));
 fireEvent.click(await screen.findByRole("button",{name:"Remove carrot"}));
 await screen.findByText("carrot removed. A notification has been sent.");
 await screen.findByText("No staff assigned yet.");
 expect(fetch.mock.calls[1][0]).toBe("/api/v1/staff/events/event1/assignments/staff1");
 expect(fetch.mock.calls[1][1].method).toBe("PUT");
 expect(fetch.mock.calls[3][1].method).toBe("DELETE");
});
it("keeps assignments visible if removal fails", async () => {
 vi.stubGlobal("fetch",vi.fn().mockResolvedValueOnce(response(true)).mockResolvedValueOnce(new Response(JSON.stringify({detail:"Unable to remove staff"}),{status:500})));
 mount(); fireEvent.click(await screen.findByRole("button",{name:"Remove carrot"}));
 await screen.findByRole("alert");
 expect(screen.getByRole("button",{name:"Remove carrot"})).toBeTruthy();
 expect(screen.queryByText(/removed. A notification/)).toBeNull();
});
it("shows the administrator role prerequisite for an empty staff directory", async () => {
 vi.stubGlobal("fetch",vi.fn().mockResolvedValue(new Response(JSON.stringify({eventTitle:"Workshop",staff:[]}))));
 mount(); await screen.findByText(/An administrator can grant/);
 expect(screen.queryByRole("button",{name:"Assign staff"})).toBeNull();
});
it("retries a failed directory load", async () => {
 const fetch=vi.fn().mockResolvedValueOnce(new Response("{}",{status:500})).mockResolvedValueOnce(response());
 vi.stubGlobal("fetch",fetch); mount();
 fireEvent.click(await screen.findByRole("button",{name:"Try again"}));
 await screen.findByLabelText("Staff member");
 await waitFor(()=>expect(fetch).toHaveBeenCalledTimes(2));
});
