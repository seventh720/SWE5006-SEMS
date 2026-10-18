import { useEffect, useRef, useState } from "react";
import { Link, Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { NotificationEntry } from "../notifications/NotificationsPage";
import { apiRequest } from "../../shared/api/client";

type AssignedEvent = { id: string; title: string; startsAt: string; endsAt: string };
type Verification = { ticketId: string; bookingId: string; status: string; checkedInAt: string | null };
type CheckInRecord = { ticketId: string; bookingId: string; operatorId: string; checkedInAt: string };
type BarcodeDetectorLike = { detect: (image: HTMLVideoElement) => Promise<Array<{ rawValue: string }>> };

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
  const [busy, setBusy] = useState(false);
  const [scanning, setScanning] = useState(false);
  const video = useRef<HTMLVideoElement>(null);
  const stream = useRef<MediaStream | null>(null);
  const detector = useRef<BarcodeDetectorLike | null>(null);
  const selected = events.find(e => e.id === eventId);
  const inWindow = selected ? Date.now() >= Date.parse(selected.startsAt) && Date.now() < Date.parse(selected.endsAt) : false;

  useEffect(() => {
    if (!token) return;
    apiRequest<AssignedEvent[]>("/api/v1/staff/events", {}, token).then(setEvents).catch(e => setMessage(e.message));
  }, [token]);
  useEffect(() => {
    setVerified(null); setCode(""); setRecords([]);
    if (!token || !eventId) return;
    apiRequest<CheckInRecord[]>(`/api/v1/staff/events/${eventId}/check-ins`, {}, token).then(setRecords).catch(e => setMessage(e.message));
  }, [eventId, token]);
  useEffect(() => () => { stream.current?.getTracks().forEach(t => t.stop()); }, []);

  async function verify() {
    if (!eventId || !code.trim() || !token) return;
    setBusy(true); setVerified(null); setMessage("");
    try {
      const result = await apiRequest<Verification>(`/api/v1/staff/events/${eventId}/verify`,
          { method: "POST", body: JSON.stringify({ code: code.trim() }) }, token);
      setVerified(result);
      setMessage(result.status === "VALID" ? "Ticket valid. Confirm check-in to admit attendee." : `Ticket cannot be admitted: ${result.status}`);
    } catch (e) { setMessage(e instanceof Error ? e.message : "Verification failed"); }
    finally { setBusy(false); }
  }
  async function checkIn() {
    if (!verified || verified.status !== "VALID" || !token) return;
    setBusy(true); setMessage("");
    try {
      await apiRequest(`/api/v1/staff/events/${eventId}/check-ins`, { method: "POST", body: JSON.stringify({ code: verified.ticketId }) }, token);
      setMessage("Check-in successful"); setVerified(null); setCode("");
      setRecords(await apiRequest<CheckInRecord[]>(`/api/v1/staff/events/${eventId}/check-ins`, {}, token));
    } catch (e) { setMessage(e instanceof Error ? e.message : "Check-in failed"); setVerified(null); }
    finally { setBusy(false); }
  }
  function stopScan() { stream.current?.getTracks().forEach(t => t.stop()); stream.current = null; setScanning(false); }
  async function startScan() {
    if (!navigator.mediaDevices?.getUserMedia) { setMessage("Camera is unavailable. Enter the ticket code manually."); return; }
    const Detector = (globalThis as typeof globalThis & { BarcodeDetector?: new (options: { formats: string[] }) => BarcodeDetectorLike }).BarcodeDetector;
    if (!Detector) { setMessage("QR scanning is not supported by this browser. Enter the code manually."); return; }
    try {
      detector.current = new Detector({ formats: ["qr_code"] });
      stream.current = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
      setScanning(true); setMessage("Point your camera at the ticket QR code.");
    } catch (e) { setMessage(e instanceof Error ? e.message : "Unable to open camera"); stopScan(); }
  }
  useEffect(() => {
    if (!scanning || !video.current || !stream.current) return;
    const player = video.current; player.srcObject = stream.current; void player.play().catch(() => setMessage("Camera playback failed"));
    let active = true;
    const timer = setInterval(async () => {
      if (!active || player.readyState < 2 || !detector.current) return;
      try { const results = await detector.current.detect(player); if (results[0]?.rawValue) { setCode(results[0].rawValue); setVerified(null); stopScan(); setMessage("QR code detected. Select Verify ticket."); } }
      catch { /* A frame can be temporarily unavailable. */ }
    }, 500);
    return () => { active = false; clearInterval(timer); };
  }, [scanning]);

  return <main className="app-shell staff-shell">
    <Link className="text-link" to="/">← My dashboard</Link>
    <header className="app-header organizer-header"><div><p className="eyebrow">Event operations</p><h1>Staff workspace</h1><p>Verify tickets and review check-ins for assigned events.</p></div><NotificationEntry /></header>
    <nav className="staff-navigation" aria-label="Staff workspace sections"><a className="text-link" href="#assigned-events">My events</a><a className="text-link" href="#ticket-verification">Ticket verification</a><a className="text-link" href="#check-in-records">Check-in records</a></nav>
    <section className="card staff-assignments" id="assigned-events"><h2>My assigned events</h2>
      {events.length === 0 ? <p>No event assignments yet. Ask an organizer to assign you to an event.</p> : <label htmlFor="staff-event">Choose event <select id="staff-event" value={eventId} onChange={e => { stopScan(); setEventId(e.target.value); setMessage(""); }}><option value="">Select an event</option>{events.map(e => <option key={e.id} value={e.id}>{e.title}</option>)}</select></label>}
      {selected && <p className="muted">{new Date(selected.startsAt).toLocaleString()} – {new Date(selected.endsAt).toLocaleString()}{!inWindow && " · Check-in is available only during the event."}</p>}
    </section>
    <div className="staff-workspace-grid"><section className="card" id="ticket-verification"><p className="section-label">Arrival desk</p><h2>Ticket verification</h2>
      {scanning && <video ref={video} autoPlay playsInline muted style={{ width: "100%", maxHeight: 300 }} aria-label="QR scanner camera" />}
      <button className="primary-button" type="button" disabled={!eventId || !inWindow} onClick={scanning ? stopScan : startScan}>{scanning ? "Stop scanner" : "Open scanner"}</button>
      <label htmlFor="staff-ticket-code">Or enter a ticket code<input id="staff-ticket-code" placeholder="Ticket UUID or sems:ticket:UUID" value={code} onChange={e => { setCode(e.target.value); setVerified(null); }} disabled={!eventId || !inWindow} /></label>
      <button className="secondary-button" type="button" disabled={!eventId || !inWindow || !code.trim() || busy} onClick={verify}>Verify ticket</button>
      {message && <p role="status">{message}</p>}
      {verified && <div><p>Ticket: {verified.ticketId}</p><p>Status: {verified.status}</p><button className="primary-button" type="button" disabled={verified.status !== "VALID" || busy} onClick={checkIn}>Confirm check-in</button></div>}
    </section><section className="card" id="check-in-records"><p className="section-label">Attendance</p><h2>Check-in records</h2>
      {!eventId ? <p>Select an assigned event to view records.</p> : records.length === 0 ? <p>No check-ins recorded yet.</p> : <ul>{records.map(r => <li key={r.ticketId}><strong>{r.ticketId}</strong><p>{new Date(r.checkedInAt).toLocaleString()} · Operator: {r.operatorId}</p></li>)}</ul>}
      <p className="muted">Already used or voided tickets cannot be admitted again.</p>
    </section></div>
  </main>;
}
