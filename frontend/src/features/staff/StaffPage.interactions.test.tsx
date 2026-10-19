// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { StaffPage } from './StaffPage';
const api = vi.hoisted(() => vi.fn());
vi.mock('../../shared/api/client', () => ({ apiRequest: api }));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({token:'token',user:{roles:['STAFF']}}) }));
vi.mock('../notifications/NotificationsPage', () => ({ NotificationEntry: () => null }));
afterEach(() => { cleanup(); vi.useRealTimers(); api.mockReset(); });
it('must not confirm an earlier ticket after the input has changed', async () => {
 let resolve!: (x:unknown)=>void;
 api.mockImplementation((path:string) => path.endsWith('/events') ? Promise.resolve([{id:'event',title:'Event',startsAt:new Date().toISOString(),endsAt:new Date(Date.now()+3600000).toISOString()}]) : path.endsWith('/verify') ? new Promise(r => {resolve=r}) : Promise.resolve([]));
 render(<MemoryRouter><StaffPage /></MemoryRouter>);
 fireEvent.click(await screen.findByRole('combobox', {name:'Choose event'}));
 fireEvent.click(screen.getByRole('option', {name:/^Event$/}));
 await act(async()=>{});
 fireEvent.change(screen.getByLabelText('Or enter a ticket code'),{target:{value:'ticket-A'}});
 fireEvent.click(screen.getByRole('button',{name:'Verify ticket'}));
 fireEvent.change(screen.getByLabelText('Or enter a ticket code'),{target:{value:'ticket-B'}});
 await act(async()=>resolve({ticketId:'ticket-A',bookingId:'booking-A',status:'VALID',checkedInAt:null}));
 expect(screen.queryByRole('button',{name:'Confirm check-in'})).toBeNull();
});
it('enables check-in 15 minutes before the event starts while the page stays open', async () => {
 vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-19T10:44:00Z'));
 api.mockImplementation((path:string)=>Promise.resolve(path.endsWith('/events') ? [{id:'event',title:'Event',startsAt:'2026-10-19T11:00:00Z',endsAt:'2026-10-19T12:00:00Z'}] : []));
 await act(async()=>{render(<MemoryRouter><StaffPage /></MemoryRouter>)});
 fireEvent.click(screen.getByRole('combobox', {name:'Choose event'}));
 fireEvent.click(screen.getByRole('option', {name:/^Event$/}));
 await act(async()=>{});
 await act(async()=>vi.advanceTimersByTime(61000));
 expect((screen.getByLabelText('Or enter a ticket code') as HTMLInputElement).disabled).toBe(false);
});

it('disables check-in exactly 15 minutes after the event starts', async () => {
 vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-19T11:14:59Z'));
 api.mockImplementation((path:string)=>Promise.resolve(path.endsWith('/events') ? [{id:'event',title:'Event',startsAt:'2026-10-19T11:00:00Z',endsAt:'2026-10-19T12:00:00Z'}] : []));
 await act(async()=>{render(<MemoryRouter><StaffPage /></MemoryRouter>)});
 fireEvent.click(screen.getByRole('combobox', {name:'Choose event'}));
 fireEvent.click(screen.getByRole('option', {name:/^Event$/}));
 await act(async()=>{});
 expect((screen.getByLabelText('Or enter a ticket code') as HTMLInputElement).disabled).toBe(false);
 await act(async()=>vi.advanceTimersByTime(1000));
 expect((screen.getByLabelText('Or enter a ticket code') as HTMLInputElement).disabled).toBe(true);
});
