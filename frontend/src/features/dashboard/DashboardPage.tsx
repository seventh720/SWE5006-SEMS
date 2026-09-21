import { useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiRequest, ApiError } from "../../shared/api/client";
import { roleLabel } from "../../shared/auth/roles";
import type { User } from "../../shared/types/auth";
import { formatEventTime, type EventPage, type PublishedEvent } from "../events/events";
import type { ManagedEvent } from "../events/drafts";

type View = "personal" | "organizer" | "staff" | "admin";
const labels: Record<View, string> = { personal: "Personal", organizer: "Organizer", staff: "Staff", admin: "Administration" };

function DataPanel<T>({ path, authenticated = false, children }: { path: string; authenticated?: boolean; children: (data: T) => ReactNode }) {
  const { token, logout } = useAuth();
  const [result, setResult] = useState<{ data?: T; error?: unknown } | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    apiRequest<T>(path, { signal: controller.signal }, authenticated ? token ?? undefined : undefined)
      .then((data) => { if (!controller.signal.aborted) setResult({ data }); })
      .catch((error: unknown) => { if (!controller.signal.aborted) setResult({ error }); });
    return () => controller.abort();
  }, [path, authenticated, token, attempt]);
  if (!result) return <p className="card" role="status">Loading your overview…</p>;
  if (result.error) {
    const expired = result.error instanceof ApiError && result.error.status === 401;
    const forbidden = result.error instanceof ApiError && result.error.status === 403;
    return <section className="card"><p role="alert" className="error-message">{expired ? "Your session has expired. Please sign in again." : forbidden ? "Your account cannot access this overview. Sign in again after a role change." : "We couldn't load this overview. Please try again."}</p>
      {expired || forbidden ? <button className="secondary-button" onClick={logout}>Sign in again</button> : <button className="secondary-button" onClick={() => setAttempt((value) => value + 1)}>Try again</button>}
    </section>;
  }
  return result.data ? <>{children(result.data)}</> : null;
}

function EventCards({ events, manage = false }: { events: (PublishedEvent | ManagedEvent)[]; manage?: boolean }) {
  return <div className="event-grid">{events.map((event) => <article className="card event-card" key={event.id}>
    <p className="section-label">{formatEventTime(event.startsAt)} · SGT</p>
    <h3><Link className="text-link" to={`${manage ? "/organizer" : ""}/events/${event.id}`}>{event.title}</Link></h3>
    <p>{event.location}</p><Link className="text-link" to={`${manage ? "/organizer" : ""}/events/${event.id}`}>{manage ? "Manage event →" : "View details →"}</Link>
  </article>)}</div>;
}

function Stats({ items }: { items: { label: string; value: number }[] }) {
  return <dl className="dashboard-stats">{items.map(({ label, value }) => <div className="card" key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>;
}

function PersonalDashboard() {
  return <>
    <section className="event-hero"><p className="eyebrow">Discover and connect</p><h2>Find your next experience</h2><p>Explore events, meet new people, and make time for something you enjoy.</p><div className="button-row"><Link className="primary-button compact button-link" to="/events">Explore all events →</Link></div></section>
    <div className="event-results-heading"><h2>Discover published events</h2><Link className="text-link" to="/events">View all</Link></div>
    <DataPanel<EventPage> path="/api/v1/events?page=0&size=4">{(data) => data.items.length ? <EventCards events={data.items} /> : <section className="card event-feedback"><h3>New experiences are on the way</h3><p>Check back soon for published events.</p></section>}</DataPanel>
  </>;
}

interface OrganizerSummary { drafts: number; published: number; cancelled: number; upcoming: ManagedEvent[] }
function OrganizerDashboard() {
  return <>
    <section className="event-hero"><p className="eyebrow">Your organizer workspace</p><h2>Bring your next event to life</h2><p>Prepare drafts, publish your events, and keep track of what is coming up.</p><div className="button-row"><Link className="primary-button compact button-link" to="/organizer/events/new">Create event</Link><Link className="secondary-button button-link" to="/organizer/events">My events →</Link></div></section>
    <DataPanel<OrganizerSummary> path="/api/v1/organizer/events/summary" authenticated>{(data) => <>
      <Stats items={[{ label: "Drafts", value: data.drafts }, { label: "Published", value: data.published }, { label: "Cancelled", value: data.cancelled }]} />
      {data.drafts > 0 && <p className="result-message">You have {data.drafts} saved drafts. <Link className="text-link" to="/organizer/events">Review your events →</Link></p>}
      <div className="event-results-heading"><h2>Your upcoming published events</h2><Link className="text-link" to="/organizer/events">Manage all</Link></div>
      {data.upcoming.length ? <EventCards events={data.upcoming} manage /> : <section className="card event-feedback"><h3>No upcoming published events</h3><p>Save a draft and publish it when you are ready.</p></section>}
    </>}</DataPanel>
  </>;
}

function AdminDashboard() {
  return <>
    <section className="event-hero"><p className="eyebrow">Administration</p><h2>Manage your community</h2><p>Review accounts and assign the roles people need.</p><div className="button-row"><Link className="primary-button compact button-link" to="/admin/users">Manage users and roles →</Link></div></section>
    <DataPanel<User[]> path="/api/v1/admin/users" authenticated>{(users) => <>
      <Stats items={[{ label: "Registered accounts", value: users.length }, { label: "Organizer accounts", value: users.filter((user) => user.roles.includes("ORGANIZER")).length }, { label: "Staff accounts", value: users.filter((user) => user.roles.includes("STAFF")).length }]} />
      <section className="card"><h2>Role management</h2><p>Accounts can hold multiple roles. After a role change, ask the user to sign in again.</p><p>Use the Organizer view to manage your own events.</p></section>
    </>}</DataPanel>
  </>;
}

function StaffDashboard() {
  return <section className="card staff-overview"><p className="eyebrow">Staff workspace</p><h2>Your event-day workspace</h2><p>Activity assignments, check-in, and ticket verification are not available yet.</p><p className="muted">Your staff role is active. Assigned tasks will appear here when these features become available.</p><Link className="text-link" to="/events">Browse published events →</Link></section>;
}

export function DashboardPage() {
  const { user, token, logout } = useAuth();
  const [selected, setSelected] = useState<View | null>(null);
  if (!user || !token) return null;
  const views: View[] = ["personal"];
  if (user.roles.includes("ORGANIZER") || user.roles.includes("ADMIN")) views.push("organizer");
  if (user.roles.includes("STAFF")) views.push("staff");
  if (user.roles.includes("ADMIN")) views.push("admin");
  const preferred: View = user.roles.includes("ADMIN") ? "admin" : user.roles.includes("ORGANIZER") ? "organizer" : user.roles.includes("STAFF") ? "staff" : "personal";
  const view = selected && views.includes(selected) ? selected : preferred;
  return <main className="app-shell">
    <header className="app-header dashboard-header"><div><p className="eyebrow">SEMS · {labels[view]}</p><h1>Welcome, {user.username}</h1></div>
      <details className="dashboard-account"><summary>My account</summary><div className="card"><strong>{user.username}</strong><p>{user.email}</p><p className="muted">{user.status}</p><p>{user.roles.map(roleLabel).join(" · ")}</p><button className="secondary-button" onClick={logout}>Logout</button></div></details>
    </header>
    {views.length > 1 && <nav className="dashboard-views" aria-label="Dashboard view">{views.map((item) => <button className="secondary-button" key={item} aria-pressed={view === item} onClick={() => setSelected(item)}>{labels[item]}</button>)}</nav>}
    <section key={`${user.id}-${view}`} aria-label={`${labels[view]} overview`}>
      {view === "personal" ? <PersonalDashboard /> : view === "organizer" ? <OrganizerDashboard /> : view === "admin" ? <AdminDashboard /> : <StaffDashboard />}
    </section>
  </main>;
}
