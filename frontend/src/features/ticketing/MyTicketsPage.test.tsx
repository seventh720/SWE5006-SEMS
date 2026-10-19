// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { MyTicketsPage } from "./MyTicketsPage";
const qr=vi.hoisted(()=>vi.fn().mockResolvedValue("data:image/png;base64,AA=="));
vi.mock("qrcode",()=>({toDataURL:qr}));
vi.mock("../auth/AuthContext",()=>({useAuth:()=>({token:"token"})}));
afterEach(()=>{cleanup(); vi.unstubAllGlobals(); qr.mockClear();});
const ticketId="12345678-1234-1234-1234-123456789abc";
const booking={id:"order",eventTitle:"Workshop",eventLocation:"Singapore",eventStartsAt:"2030-01-01T10:00:00Z",eventEndsAt:"2030-01-01T12:00:00Z",status:"CONFIRMED"};
const response=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status});
function mount(){render(<MemoryRouter initialEntries={["/bookings/order/tickets"]}><Routes><Route path="/bookings/:id/tickets" element={<MyTicketsPage/>}/></Routes></MemoryRouter>);}
it("shows the precise ticket QR payload and current event details",async()=>{
 vi.stubGlobal("fetch",vi.fn().mockImplementation((url:string)=>Promise.resolve(response(url.endsWith("/tickets")?[{id:ticketId,ordinal:1,status:"VALID"}]:{...booking,currentEvent:{title:"Changed title",location:"New venue",startsAt:booking.eventStartsAt,endsAt:booking.eventEndsAt}}))));
 mount();
 await screen.findByAltText("Ticket QR code");
 expect(qr).toHaveBeenCalledWith("sems:ticket:"+ticketId,expect.objectContaining({margin:4}));
 expect(screen.getByText("New venue")).toBeTruthy();
});
it("refresh removes QR codes after ticket cancellation",async()=>{
 let cancelled=false;
 vi.stubGlobal("fetch",vi.fn().mockImplementation((url:string)=>Promise.resolve(response(url.endsWith("/tickets")?[{id:ticketId,ordinal:1,status:cancelled?"VOID":"VALID"}]:{...booking,status:cancelled?"CANCELLED":"CONFIRMED"}))));
 mount(); await screen.findByAltText("Ticket QR code");
 cancelled=true;fireEvent.click(screen.getByRole("button",{name:"Refresh tickets"}));
 await screen.findByText("Voided");
 expect(screen.queryByAltText("Ticket QR code")).toBeNull();
});
it("does not show an admission QR for used tickets",async()=>{
 vi.stubGlobal("fetch",vi.fn().mockImplementation((url:string)=>Promise.resolve(response(url.endsWith("/tickets")?[{id:ticketId,ordinal:1,status:"CHECKED_IN"}]:booking))));
 mount();await screen.findByText("Checked in");
 expect(qr).not.toHaveBeenCalled();
});
it("hides stale QR codes if the refresh fails",async()=>{
 let failed=false;
 vi.stubGlobal("fetch",vi.fn().mockImplementation((url:string)=>Promise.resolve(failed?response({},403):response(url.endsWith("/tickets")?[{id:ticketId,ordinal:1,status:"VALID"}]:booking))));
 mount();await screen.findByAltText("Ticket QR code");
 failed=true;fireEvent.click(screen.getByRole("button",{name:"Refresh tickets"}));
 await screen.findByRole("alert");expect(screen.queryByAltText("Ticket QR code")).toBeNull();
});
