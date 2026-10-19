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
 api.mockImplementation((path:string) => path.endsWith('/events') ? Promise.resolve([{id:'event',title:'Event',startsAt:'2020-01-01',endsAt:'2099-01-01'}]) : path.endsWith('/verify') ? new Promise(r => {resolve=r}) : Promise.resolve([]));
 render(<MemoryRouter><StaffPage /></MemoryRouter>);
 fireEvent.change(await screen.findByLabelText('Choose event'), {target:{value:'event'}});
 await act(async()=>{});
 fireEvent.change(screen.getByLabelText('Or enter a ticket code'),{target:{value:'ticket-A'}});
 fireEvent.click(screen.getByRole('button',{name:'Verify ticket'}));
 fireEvent.change(screen.getByLabelText('Or enter a ticket code'),{target:{value:'ticket-B'}});
 await act(async()=>resolve({ticketId:'ticket-A',bookingId:'booking-A',status:'VALID',checkedInAt:null}));
 expect(screen.queryByRole('button',{name:'Confirm check-in'})).toBeNull();
});
it('enables check-in when the event starts while the page stays open', async () => {
 vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-19T10:59:00Z'));
 api.mockImplementation((path:string)=>Promise.resolve(path.endsWith('/events') ? [{id:'event',title:'Event',startsAt:'2026-10-19T11:00:00Z',endsAt:'2026-10-19T12:00:00Z'}] : []));
 await act(async()=>{render(<MemoryRouter><StaffPage /></MemoryRouter>)});
 fireEvent.change(screen.getByLabelText('Choose event'),{target:{value:'event'}});
 await act(async()=>{});
 await act(async()=>vi.advanceTimersByTime(61000));
 expect((screen.getByLabelText('Or enter a ticket code') as HTMLInputElement).disabled).toBe(false);
});
