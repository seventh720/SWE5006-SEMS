import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { useManagementRequest } from "../events/OrganizerPages";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../../shared/api/client";
import { managementError } from "../events/drafts";

export interface EventStatistics {
  eventId: string;
  eventStatus: "DRAFT" | "PUBLISHED" | "CANCELLED";
  capacity: number;
  validReservedTickets: number;
  cancelledTickets: number;
  checkedInTickets: number;
}

export function OrganizerStatisticsPage() {
  const { id } = useParams();
  const request = useManagementRequest<EventStatistics>(
    `/api/v1/organizer/events/${encodeURIComponent(id ?? "")}/statistics`);
  return <main className="app-shell">
    <nav className="event-nav" aria-label="Statistics navigation">
      <Link className="text-link" to="/organizer/events">← My events</Link>
      <Link className="text-link" to={`/organizer/events/${id ?? ""}`}>← Event details</Link>
    </nav>
    <header className="app-header organizer-header">
      <div>
        <p className="eyebrow">Organizer workspace</p>
        <h1>Event statistics</h1>
        <p>Reserved, cancelled and checked-in ticket counts for this event.</p>
      </div>
    </header>
    {request.loading && <p role="status">Loading statistics…</p>}
    {request.error != null && <section className="card"><StatisticsError error={request.error} reload={request.reload} /></section>}
    {request.data && <StatisticsView statistics={request.data} />}
  </main>;
}

function StatisticsError({ error, reload }: { error: unknown; reload: () => void }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const status = error instanceof ApiError ? error.status : null;
  const message = status === 403
    ? "You need an organizer or administrator role to view event statistics."
    : managementError(error);
  return <div role="alert">
    <p className="error-message">{message}</p>
    {status === 401 && <button type="button" className="secondary-button"
      onClick={() => { logout(); navigate("/login", { state: { from: location } }); }}>Sign in again</button>}
    {status === 403 && <Link className="text-link" to="/">Return to dashboard</Link>}
    {status !== 401 && status !== 403 && <button type="button" className="secondary-button" onClick={reload}>Try again</button>}
  </div>;
}

function StatisticsView({ statistics }: { statistics: EventStatistics }) {
  const statusClass = `status-${statistics.eventStatus.toLowerCase()}`;
  return <>
    <section className="card" aria-label="Event status">
      <h2>Event status</h2>
      <p><span className={`event-status ${statusClass}`}>{statistics.eventStatus}</span></p>
    </section>
    <dl className="dashboard-stats" aria-label="Ticket statistics">
      <div className="card"><dt>Capacity</dt><dd>{statistics.capacity}</dd></div>
      <div className="card"><dt>Valid reserved tickets</dt><dd>{statistics.validReservedTickets}</dd></div>
      <div className="card"><dt>Cancelled tickets</dt><dd>{statistics.cancelledTickets}</dd></div>
      <div className="card"><dt>Checked-in tickets</dt><dd>{statistics.checkedInTickets}</dd></div>
    </dl>
    <section className="card">
      <h2>About these numbers</h2>
      <p className="muted">Reserved and cancelled counts are ticket quantities, not order counts. Checked-in counts only currently valid CHECKED_IN tickets. Capacity is the event size, not ticket availability. Zero is a normal value, not missing data.</p>
    </section>
  </>;
}
