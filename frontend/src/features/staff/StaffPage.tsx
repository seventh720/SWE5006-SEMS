import { Select } from "../../shared/components/Select";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, Navigate, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { NotificationEntry } from "../notifications/NotificationsPage";
import { TicketCamera } from "./TicketCamera";
import { formatEventTime } from "../events/events";
import { EventPhase } from "../events/EventPhase";
import { apiRequest } from "../../shared/api/client";

type AssignedEvent = { id: string; title: string; startsAt: string; endsAt: string; status?: string };
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
  const { token, logout } = useAuth();
  const navigate = useNavigate();
  const [refresh, setRefresh] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [denied, setDenied] = useState(0);
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
  const interaction = useRef(false);
  interaction.current = busy || checkingIn || scanning;
  const previousEvent = useRef("");
  const [now, setNow] = useState(Date.now);
  const selected = events.find(e => e.id === eventId);
  const inWindow = selected && !denied && (!selected.status || selected.status === "PUBLISHED") ? now >= Date.parse(selected.startsAt) - 15 * 60_000 && now < Math.min(Date.parse(selected.startsAt) + 15 * 60_000, Date.parse(selected.endsAt)) : false;
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

  function accessError(error: unknown) {
    const status = typeof error === "object" && error !== null && "status" in error ? Number(error.status) : 0;
    if (status === 401 || status === 403) {
      setDenied(status); setRecords([]); setScanning(false); setVerified(null); generation.current++;
    }
    return error instanceof Error ? error.message : "Unable to refresh staff workspace";
  }
  useEffect(() => {
    const update = () => { if (!interaction.current && !document.hidden) setRefresh(value => value + 1); };
    const timer = setInterval(update, 30_000);
    window.addEventListener("focus", update);
    document.addEventListener("visibilitychange", update);
    return () => { clearInterval(timer); window.removeEventListener("focus", update); document.removeEventListener("visibilitychange", update); };
  }, []);
  useEffect(() => {
    generation.current++; setBusy(false); setVerified(null); setCode(""); setRecords([]); setScanning(false);
    previousEvent.current = "";
  }, [eventId, token]);
  useEffect(() => {
    if (!token) return;
    const controller = new AbortController();
    setRefreshing(true);
    setLoadError("");
    async function update() {
      try {
        const assigned = await apiRequest<AssignedEvent[]>("/api/v1/staff/events", { signal: controller.signal }, token!);
        if (controller.signal.aborted) return;
        setEvents(assigned); setDenied(0);
        const current = assigned.find(event => event.id === eventId);
        if (eventId && !current) {
          generation.current++; setVerified(null); setCode(""); setRecords([]); setScanning(false);
          setEventId(""); setMessage("Your assignment has been removed. Select another event."); return;
        }
        if (current) {
          const key = `${current.id}:${current.startsAt}:${current.endsAt}:${current.status}`;
          if (previousEvent.current && key !== previousEvent.current) {
            generation.current++; setVerified(null); setCode(""); setScanning(false);
            setMessage("Event details changed. Verify the ticket again before check-in.");
          }
          previousEvent.current = key;
          const requestGeneration = generation.current;
          const history = await apiRequest<CheckInRecord[]>(`/api/v1/staff/events/${eventId}/check-ins`, { signal: controller.signal }, token!);
          if (!controller.signal.aborted && requestGeneration === generation.current) setRecords(history);
        }
      } catch (error) {
        if (!controller.signal.aborted) setLoadError(accessError(error));
      } finally { if (!controller.signal.aborted) setRefreshing(false); }
    }
    void update();
    return () => controller.abort();
  }, [token, eventId, refresh]);
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
    } catch (e) { if (request === generation.current) setMessage(accessError(e)); }
    finally { if (request === generation.current) setBusy(false); }
  }
  async function checkIn() {
    if (!verified || verified.status !== "VALID" || !token || !inWindow) return;
    setCheckingIn(true); setBusy(true); setMessage("");
    try {
      await apiRequest(`/api/v1/staff/events/${eventId}/check-ins`, { method: "POST", body: JSON.stringify({ code: verified.ticketId }) }, token);
      setMessage("Check-in successful"); setVerified(null); setCode("");
      setRecords(await apiRequest<CheckInRecord[]>(`/api/v1/staff/events/${eventId}/check-ins`, {}, token));
    } catch (e) { setMessage(accessError(e)); setVerified(null); }
    finally { setCheckingIn(false); setBusy(false); }
  }

  return <main className="app-shell staff-shell">
    <Link className="text-link" to="/">← My dashboard</Link>
    <header className="app-header organizer-header"><div><p className="eyebrow">Event operations</p><h1>Staff workspace</h1><p>Verify tickets and review check-ins for assigned events.</p></div><NotificationEntry /></header>
    <nav className="staff-navigation" aria-label="Staff workspace sections"><a className="text-link" href="#assigned-events">My events</a><a className="text-link" href="#ticket-verification">Ticket verification</a><a className="text-link" href="#check-in-records">Check-in records</a></nav>
    <section className="card staff-assignments" id="assigned-events"><h2>My assigned events</h2>
      <button className="secondary-button" disabled={refreshing || busy || checkingIn} onClick={() => { stopScan(); setRefresh(value => value + 1); }}>Refresh workspace</button>
      <p className="muted">Assignments and records refresh every 30 seconds and when you return to this page. Automatic refresh pauses while scanning or checking a ticket.</p>
      {refreshing && <p role="status">Refreshing workspace…</p>}
      {loadError && <p role="alert" className="error-message">{loadError}</p>}
      {denied === 401 && <button className="secondary-button" onClick={() => { logout(); navigate("/login"); }}>Sign in again</button>}
      {denied === 403 && <Link className="text-link" to="/">Return to dashboard</Link>}
      {events.length === 0 ? (!refreshing && !loadError && <p>No event assignments yet. Ask an organizer to assign you to an event.</p>) : <Select label="Choose event" value={eventId} disabled={busy || checkingIn || refreshing}
        onChange={value => { generation.current++; stopScan(); setEventId(value); setMessage(""); }}
        options={[{ value: "", label: "Select an event" }, ...events.map(event => ({ value: event.id, label: event.title }))]} />}
      {selected && <EventPhase startsAt={selected.startsAt} endsAt={selected.endsAt} status={selected.status} />}
      {selected && <p className="muted">{formatEventTime(selected.startsAt)} – {formatEventTime(selected.endsAt)} · SGT{!inWindow && " · Check-in opens 15 minutes before the event and closes 15 minutes after it starts (or at its end, if earlier)."}</p>}
    </section>
    <div className="staff-workspace-grid"><section className="card" id="ticket-verification"><p className="section-label">Arrival desk</p><h2>Ticket verification</h2>
      {scanning && <TicketCamera onClose={stopScan} onScan={value => { changeCode(value); stopScan(); setMessage("QR code detected. Select Verify ticket."); }} />}
      {!scanning && <button className="primary-button" type="button" disabled={!eventId || !inWindow || busy || checkingIn || refreshing} onClick={() => { changeCode(""); setScanning(true); }}>Open scanner</button>}
      <label htmlFor="staff-ticket-code">Or enter a ticket code<input id="staff-ticket-code" placeholder="Ticket UUID or sems:ticket:UUID" value={code} onChange={e => changeCode(e.target.value)} disabled={!eventId || !inWindow || checkingIn} /></label>
      <button className="secondary-button" type="button" disabled={!eventId || !inWindow || !code.trim() || busy || checkingIn || refreshing} onClick={verify}>Verify ticket</button>
      {message && <p role="status">{message}</p>}
      {verified && <div><p>Ticket: {verified.ticketId}</p><p>Status: {verified.status}</p><button className="primary-button" type="button" disabled={verified.status !== "VALID" || busy || checkingIn || !inWindow || refreshing} onClick={checkIn}>Confirm check-in</button></div>}
    </section><section className="card" id="check-in-records"><p className="section-label">Attendance</p><h2>Check-in records</h2>
      {!eventId ? <p>Select an assigned event to view records.</p> : records.length === 0 ? <p>No check-ins recorded yet.</p> : <ul>{records.map(r => <li key={r.ticketId}><strong>{r.ticketId}</strong><p>{formatEventTime(r.checkedInAt)} · SGT · Operator: {r.operatorId}</p></li>)}</ul>}
      <p className="muted">Already used or voided tickets cannot be admitted again.</p>
    </section></div>
  </main>;
}
