import { Link, Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { NotificationEntry } from "../notifications/NotificationsPage";

export function StaffRoute() {
  const { user } = useAuth();
  return user?.roles.includes("STAFF") ? <Outlet /> : <Navigate to="/" replace />;
}

export function StaffOverview() {
  return <section className="event-hero">
    <p className="eyebrow">Staff workspace</p>
    <h2>Your event-day workspace</h2>
    <p>Find your assigned events and help attendees check in.</p>
    <div className="button-row"><Link className="primary-button compact button-link" to="/staff">Open staff workspace →</Link></div>
  </section>;
}

export function StaffPage() {
  return <main className="app-shell staff-shell">
    <Link className="text-link" to="/">← My dashboard</Link>
    <header className="app-header organizer-header">
      <div><p className="eyebrow">Event operations</p><h1>Staff workspace</h1><p>Manage your arrival desk, verify tickets, and review check-ins.</p></div>
      <NotificationEntry />
    </header>
    <nav className="staff-navigation" aria-label="Staff workspace sections">
      <a className="text-link" href="#assigned-events">My events</a>
      <a className="text-link" href="#ticket-verification">Ticket verification</a>
      <a className="text-link" href="#check-in-records">Check-in records</a>
    </nav>
    <section className="card staff-assignments" id="assigned-events" aria-labelledby="staff-events-title">
      <div className="staff-section-heading"><h2 id="staff-events-title">My assigned events</h2><span className="event-status status-draft">Coming soon</span></div>
      <p>Event assignments are not available yet.</p>
      <p className="muted">Your organizer assigns access to individual events. A Staff role alone does not grant access to every event.</p>
    </section>
    <div className="staff-workspace-grid">
      <section className="card" id="ticket-verification" aria-labelledby="staff-verification-title">
        <p className="section-label">Arrival desk</p>
        <h2 id="staff-verification-title">Ticket verification</h2>
        <div className="staff-scanner-placeholder" aria-hidden="true">
          <svg viewBox="0 0 64 64" width="64" height="64" fill="none" stroke="currentColor" strokeWidth="3">
            <path d="M22 8H8v14M42 8h14v14M8 42v14h14M42 56h14V42M8 32h48" />
            <path d="M21 20h8v8h-8zM36 20h8v8h-8zM21 37h8v8h-8zM37 36v9h8v-5" />
          </svg>
          <span>Scan an attendee’s ticket</span>
        </div>
        <p id="staff-verification-help" className="muted">Check-in is coming soon. Select an assigned event when it becomes available to start verifying tickets.</p>
        <button className="primary-button" type="button" disabled aria-describedby="staff-verification-help">Open scanner</button>
        <label htmlFor="staff-ticket-code">Or enter a ticket code
          <input id="staff-ticket-code" placeholder="Ticket code" disabled aria-describedby="staff-verification-help" />
        </label>
        <button className="secondary-button" type="button" disabled aria-describedby="staff-verification-help">Verify ticket</button>
      </section>
      <section className="card" id="check-in-records" aria-labelledby="staff-records-title">
        <p className="section-label">Attendance</p>
        <h2 id="staff-records-title">Check-in records</h2>
        <p>Check-in records are not available yet.</p>
        <p className="muted">Once check-in is enabled, review verified tickets for the event you are working on here.</p>
        <div className="staff-guidance"><h3>Before checking in attendees</h3>
          <ol><li>Confirm the event and your assignment.</li><li>Scan the ticket or enter its code.</li><li>Wait for confirmation before admitting the attendee.</li></ol>
          <p>Already used or cancelled tickets must not be admitted again.</p>
        </div>
      </section>
    </div>
  </main>;
}
