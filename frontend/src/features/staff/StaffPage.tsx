import { useCallback, useEffect, useRef, useState } from "react";
import { Link, Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { NotificationEntry } from "../notifications/NotificationsPage";
import { TicketCamera } from "./TicketCamera";
import { apiRequest } from "../../shared/api/client";

type AssignedEvent = { id: string; title: string; startsAt: string; endsAt: string };
type Verification = { ticketId: string; bookingId: string; status: string; checkedInAt: string | null };
type CheckInRecord = { ticketId: string; bookingId: string; operatorId: string; checkedInAt: string };


export function StaffRoute() {
  const { user } = useAuth();
  return user?.roles.includes("STAFF") ? <Outlet /> : <Navigate to="/" replace />;
}

export function StaffOverview() {
  return <section className="event-hero"><p className="eyebrow">Staff workspace</p><h2>Your event-day workspace</h2>
    <p>Find your assigned events and help attendees check in.</p>
    <div className="button-row"><Link className="primary-button compact button-link" to="/staff">Open staff workspace →</Link></div>
  </section>;
}

export function StaffPage() {
  const { token } = useAuth();
  const [events, setEvents] = useState<AssignedEvent[]>([]);
  const [eventId, setEventId] = useState("");
  const [code, setCode] = useState("");
  const [verified, setVerified] = useState<Verification | null>(null);
  const [records, setRecords] = useState<CheckInRecord[]>([]);
  const [message, setMessage] = useState("");
  const [checkingIn, setCheckingIn] = useState(false);
  const [busy, setBusy] = useState(false);
  const [scanning, setScanning] = useState(false);
  const generation = useRef(0);
  const [now, setNow] = useState(Date.now);
  const selected = events.find(e => e.id === eventId);
  const inWindow = selected ? now >= Date.parse(selected.startsAt) && now < Date.parse(selected.endsAt) : false;
  const stopScan = useCallback(() => setScanning(false), []);
  function changeCode(value: string) {
    generation.current++;
    setCode(value); setVerified(null); setBusy(false); setMessage("");
  }
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => { if (!inWindow) { setScanning(false); setVerified(null); setBusy(false); generation.current++; } }, [inWindow]);

  useEffect(() => {
    if (!token) return;
    apiRequest<AssignedEvent[]>("/api/v1/staff/events", {}, token).then(setEvents).catch(e => setMessage(e.message));
  }, [token]);
  useEffect(() => {
    generation.current++; setBusy(false); setVerified(null); setCode(""); setRecords([]);
    let active = true;
    if (!token || !eventId) return;
    apiRequest<CheckInRecord[]>(`/api/v1/staff/events/${eventId}/check-ins`, {}, token).then(result => { if (active) setRecords(result); }).catch(e => { if (active) setMessage(e.message); });
    return () => { active = false; };
  }, [eventId, token]);
  useEffect(() => () => { generation.current++; }, []);

  async function verify() {
    if (!eventId || !code.trim() || !token) return;
    stopScan();
    const request = ++generation.current;
    setBusy(true); setVerified(null); setMessage("");
    try {
      const result = await apiRequest<Verification>(`/api/v1/staff/events/${eventId}/verify`,
          { method: "POST", body: JSON.stringify({ code: code.trim() }) }, token);
      if (request !== generation.current) return;
      setVerified(result);
      setMessage(result.status === "VALID" ? "Ticket valid. Confirm check-in to admit attendee." : `Ticket cannot be admitted: ${result.status}`);
    } catch (e) { if (request === generation.current) setMessage(e instanceof Error ? e.message : "Verification failed"); }
    finally { if (request === generation.current) setBusy(false); }
  }
  async function checkIn() {
    if (!verified || verified.status !== "VALID" || !token || !inWindow) return;
    setCheckingIn(true); setBusy(true); setMessage("");
    try {
      await apiRequest(`/api/v1/staff/events/${eventId}/check-ins`, { method: "POST", body: JSON.stringify({ code: verified.ticketId }) }, token);
      setMessage("Check-in successful"); setVerified(null); setCode("");
      setRecords(await apiRequest<CheckInRecord[]>(`/api/v1/staff/events/${eventId}/check-ins`, {}, token));
    } catch (e) { setMessage(e instanceof Error ? e.message : "Check-in failed"); setVerified(null); }
    finally { setCheckingIn(false); setBusy(false); }
  }

  return <main className="app-shell staff-shell">
    <Link className="text-link" to="/">← My dashboard</Link>
    <header className="app-header organizer-header"><div><p className="eyebrow">Event operations</p><h1>Staff workspace</h1><p>Verify tickets and review check-ins for assigned events.</p></div><NotificationEntry /></header>
    <nav className="staff-navigation" aria-label="Staff workspace sections"><a className="text-link" href="#assigned-events">My events</a><a className="text-link" href="#ticket-verification">Ticket verification</a><a className="text-link" href="#check-in-records">Check-in records</a></nav>
    <section className="card staff-assignments" id="assigned-events"><h2>My assigned events</h2>
      {events.length === 0 ? <p>No event assignments yet. Ask an organizer to assign you to an event.</p> : <label htmlFor="staff-event">Choose event <select id="staff-event" value={eventId} disabled={busy || checkingIn} onChange={e => { generation.current++; stopScan(); setEventId(e.target.value); setMessage(""); }}><option value="">Select an event</option>{events.map(e => <option key={e.id} value={e.id}>{e.title}</option>)}</select></label>}
      {selected && <p className="muted">{new Date(selected.startsAt).toLocaleString()} – {new Date(selected.endsAt).toLocaleString()}{!inWindow && " · Check-in is available only during the event."}</p>}
    </section>
    <div className="staff-workspace-grid"><section className="card" id="ticket-verification"><p className="section-label">Arrival desk</p><h2>Ticket verification</h2>
      {scanning && <TicketCamera onClose={stopScan} onScan={value => { changeCode(value); stopScan(); setMessage("QR code detected. Select Verify ticket."); }} />}
      {!scanning && <button className="primary-button" type="button" disabled={!eventId || !inWindow || busy || checkingIn} onClick={() => { changeCode(""); setScanning(true); }}>Open scanner</button>}
      <label htmlFor="staff-ticket-code">Or enter a ticket code<input id="staff-ticket-code" placeholder="Ticket UUID or sems:ticket:UUID" value={code} onChange={e => changeCode(e.target.value)} disabled={!eventId || !inWindow || checkingIn} /></label>
      <button className="secondary-button" type="button" disabled={!eventId || !inWindow || !code.trim() || busy || checkingIn} onClick={verify}>Verify ticket</button>
      {message && <p role="status">{message}</p>}
      {verified && <div><p>Ticket: {verified.ticketId}</p><p>Status: {verified.status}</p><button className="primary-button" type="button" disabled={verified.status !== "VALID" || busy || checkingIn || !inWindow} onClick={checkIn}>Confirm check-in</button></div>}
    </section><section className="card" id="check-in-records"><p className="section-label">Attendance</p><h2>Check-in records</h2>
      {!eventId ? <p>Select an assigned event to view records.</p> : records.length === 0 ? <p>No check-ins recorded yet.</p> : <ul>{records.map(r => <li key={r.ticketId}><strong>{r.ticketId}</strong><p>{new Date(r.checkedInAt).toLocaleString()} · Operator: {r.operatorId}</p></li>)}</ul>}
      <p className="muted">Already used or voided tickets cannot be admitted again.</p>
    </section></div>
  </main>;
}
