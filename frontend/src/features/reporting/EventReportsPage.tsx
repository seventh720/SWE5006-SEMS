import { useEffect, useState, type FormEvent } from "react";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useManagementRequest } from "../events/useManagementRequest";
import { DateTimeField } from "../events/DateTimeField";
import { EventPhase } from "../events/EventPhase";
import { formatEventTime, readEventQuery } from "../events/events";
import { Select } from "../../shared/components/Select";
import { StatisticsError } from "./OrganizerStatisticsPage";

type ReportRow = {
  eventId: string; eventTitle: string; eventStatus: string; startsAt: string; endsAt: string;
  ticketTypeId: string; ticketType: string; quota: number; available: number;
  confirmed: number; cancelled: number; checkedIn: number;
};
type Report = {
  items: ReportRow[]; totalElements: number; totalPages: number;
  totals: { confirmed: number; cancelled: number; checkedIn: number };
};
const fields = ["keyword", "ticketType", "from", "to", "bookingStatus", "eventId"] as const;

export function EventReportsPage({ admin = false }: { admin?: boolean }) {
  const { user } = useAuth();
  const allowed = user?.roles.includes("ADMIN") || (!admin && user?.roles.includes("ORGANIZER"));
  return allowed ? <ReportView admin={admin} /> : <Navigate to="/" replace />;
}

function ReportView({ admin }: { admin: boolean }) {
  const [params, setParams] = useSearchParams();
  const { page } = readEventQuery(params);
  const query = Object.fromEntries(fields.map(key => [key, params.get(key) ?? (key === "bookingStatus" ? "ALL" : "")])) as Record<typeof fields[number], string>;
  const queryKey = JSON.stringify(query);
  const [draft, setDraft] = useState(query);
  const [error, setError] = useState("");
  useEffect(() => { setDraft(JSON.parse(queryKey)); setError(""); }, [queryKey]);
  const search = new URLSearchParams({ ...query, page: String(page), size: "10" });
  if (!query.eventId) search.delete("eventId");
  if (!query.from) search.delete("from");
  if (!query.to) search.delete("to");
  const request = useManagementRequest<Report>(`/api/v1/${admin ? "admin" : "organizer"}/reports?${search}`);
  function move(next: number, filters = query) {
    const nextParams = new URLSearchParams({ page: String(next) });
    for (const key of fields) if (filters[key] && filters[key] !== "ALL") nextParams.set(key, filters[key]);
    setParams(nextParams);
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    if (draft.from && draft.to && draft.from > draft.to) { setError("From date must be on or before To date."); return; }
    setError(""); move(0, { ...draft, keyword: draft.keyword.trim(), ticketType: draft.ticketType.trim() });
  }
  return <main className="app-shell">
    <Link className="text-link" to="/">← My dashboard</Link>
    <header className="app-header organizer-header"><div><p className="eyebrow">{admin ? "Administrator" : "Organizer"} workspace</p>
      <h1>{admin ? "All event reports" : "My event reports"}</h1>
      <p>{admin ? "Ticket activity across all organizers." : "Ticket activity for events you created, including those created as an administrator."}</p></div>
      <button className="secondary-button" onClick={request.reload} disabled={request.loading}>Refresh report</button>
    </header>
    <form className="card filter-form" role="search" onSubmit={submit}>
      <label>Event title<input type="search" value={draft.keyword} onChange={e => setDraft({ ...draft, keyword: e.target.value })} /></label>
      <label>Ticket type<input type="search" value={draft.ticketType} onChange={e => setDraft({ ...draft, ticketType: e.target.value })} /></label>
      <DateTimeField dateOnly required={false} name="report-filter-from" label="From date (SGT)" value={draft.from} onChange={from => setDraft({ ...draft, from })} />
      <DateTimeField dateOnly required={false} name="report-filter-to" label="To date (SGT)" value={draft.to} onChange={to => setDraft({ ...draft, to })} />
      <Select label="Booking status" value={draft.bookingStatus} onChange={value => setDraft({ ...draft, bookingStatus: value })}
        options={[{ value: "ALL", label: "All bookings" }, { value: "CONFIRMED", label: "Confirmed" }, { value: "CANCELLED", label: "Cancelled" }]} />
      <div className="filter-actions">
        <button className="primary-button compact">Apply filters</button>
        <button type="button" className="secondary-button" onClick={() => { setParams({}); setDraft({ keyword: "", ticketType: "", from: "", to: "", bookingStatus: "ALL", eventId: "" }); setError(""); }}>Clear filters</button>
      </div>
    </form>
    {query.eventId && <p>Showing one selected event. Clear filters to include other events.</p>}
    {error && <p role="alert" className="error-message">{error}</p>}
    <p className="muted">Dates filter event start dates in Singapore time. Counts are ticket quantities, not orders. Totals cover all matching rows, across all pages. Only events with configured ticket types appear.</p>
    {request.loading && <p role="status">Loading report…</p>}
    {request.error != null && <StatisticsError error={request.error} reload={request.reload} />}
    {request.data && <>
      <dl className="dashboard-stats" aria-label="Report totals">
        <div className="card"><dt>Confirmed tickets</dt><dd>{request.data.totals.confirmed}</dd></div>
        <div className="card"><dt>Cancelled tickets</dt><dd>{request.data.totals.cancelled}</dd></div>
        <div className="card"><dt>Checked-in tickets</dt><dd>{request.data.totals.checkedIn}</dd></div>
      </dl>
      <p role="status">{request.data.totalElements} ticket types found</p>
      {request.data.items.length === 0 ? <p className="card">No matching ticket types on this page.</p> : <div className="card report-scroll">
        <table className="report-table"><caption>Event and ticket type breakdown</caption><thead><tr>
          {['Event', 'Status', 'Starts (SGT)', 'Ticket type', 'Quota', 'Remaining quota', 'Confirmed', 'Cancelled', 'Checked in'].map(title => <th key={title} scope="col">{title}</th>)}
        </tr></thead><tbody>{request.data.items.map(row => <tr key={row.ticketTypeId}>
          <th scope="row">{row.eventTitle}</th><td><EventPhase startsAt={row.startsAt} endsAt={row.endsAt} status={row.eventStatus} /></td>
          <td>{formatEventTime(row.startsAt)}</td><td>{row.ticketType}</td><td>{row.quota}</td><td>{row.available}</td><td>{row.confirmed}</td><td>{row.cancelled}</td><td>{row.checkedIn}</td>
        </tr>)}</tbody></table>
      </div>}
      <p className="muted">Quota and remaining quota show current inventory regardless of the booking status filter. Remaining quota does not mean sales are open. Cancelled events and closed registration windows cannot accept bookings.</p>
      <nav className="event-pagination" aria-label="Report pages">
        <button className="secondary-button" disabled={page === 0} onClick={() => move(page - 1)}>Previous</button>
        <span>Page {page + 1} of {Math.max(1, request.data.totalPages)}</span>
        <button className="secondary-button" disabled={page + 1 >= request.data.totalPages} onClick={() => move(page + 1)}>Next</button>
      </nav>
    </>}
  </main>;
}
