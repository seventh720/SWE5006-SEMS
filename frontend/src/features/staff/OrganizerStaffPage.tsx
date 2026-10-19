import { Select } from "../../shared/components/Select";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ManagementError } from "../events/OrganizerPages";
import { useManagementRequest } from "../events/useManagementRequest";
import { apiRequest } from "../../shared/api/client";

interface StaffMember { id: string; username: string; eligible: boolean; assigned: boolean }
interface Assignments { eventTitle: string; staff: StaffMember[] }

export function OrganizerStaffPage() {
  const { id = "" } = useParams();
  return <StaffAssignments key={id} eventId={id} />;
}

function StaffAssignments({ eventId }: { eventId: string }) {
  const { token } = useAuth();
  const path = `/api/v1/staff/events/${encodeURIComponent(eventId)}/assignments`;
  const request = useManagementRequest<Assignments>(path);
  const [selected, setSelected] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [message, setMessage] = useState("");
  const assigned = request.data?.staff.filter(member => member.assigned) ?? [];
  const available = request.data?.staff.filter(member => member.eligible && !member.assigned) ?? [];

  async function update(member: StaffMember, assign: boolean) {
    if (!token || busy) return;
    setBusy(true); setError(null); setMessage("");
    try {
      await apiRequest<void>(`${path}/${encodeURIComponent(member.id)}`, { method: assign ? "PUT" : "DELETE" }, token);
      setMessage(assign ? `${member.username} assigned. A notification has been sent.` : `${member.username} removed. A notification has been sent.`);
      setSelected(""); request.reload();
    } catch (failure) { setError(failure); }
    finally { setBusy(false); }
  }

  return <main className="app-shell">
    <Link className="text-link" to={`/organizer/events/${eventId}`}>← Event details</Link>
    <header className="app-header organizer-header"><div><p className="eyebrow">Organizer workspace</p>
      <h1>Manage staff</h1>
      {request.data && <div className="staff-event-context"><span>Event</span><h2>{request.data.eventTitle}</h2></div>}
      <p>Assign staff to verify tickets and check in attendees for this event.</p>
    </div></header>
    {message && <p role="status">{message}</p>}
    {error != null && <ManagementError error={error} />}
    {request.loading && <p role="status">Loading staff…</p>}
    {request.error != null && <section className="card"><ManagementError error={request.error} />
      <button className="secondary-button" onClick={request.reload}>Try again</button></section>}
    {request.data && <div className="staff-management-sections">
      <section className="card">
        <h2>Assign staff</h2>
        {available.length ? <form onSubmit={e => { e.preventDefault(); const member = available.find(item => item.id === selected); if (member) void update(member, true); }}>
          <Select label="Staff member" value={selected} disabled={busy} onChange={setSelected}
            options={[{ value: "", label: "Choose staff" }, ...available.map(member => ({ value: member.id, label: member.username }))]} />
          <button className="primary-button" disabled={!selected || busy} type="submit">Assign staff</button>
        </form> : <p>No unassigned staff are available. An administrator can grant the Staff role in user management.</p>}
        <p className="muted">Staff receive a notification when assigned or removed. Access applies only to this event.</p>
      </section>
      <section className="card"><h2>Assigned staff ({assigned.length})</h2>
        {!assigned.length ? <p>No staff assigned yet.</p> : <ul className="staff-assignment-list">{assigned.map(member => <li key={member.id}>
          <div><strong>{member.username}</strong>{!member.eligible && <p className="muted">Account inactive or Staff role removed.</p>}</div>
          <button className="secondary-button" disabled={busy} onClick={() => void update(member, false)} aria-label={`Remove ${member.username}`}>Remove</button>
        </li>)}</ul>}
      </section>
    </div>}
  </main>;
}
