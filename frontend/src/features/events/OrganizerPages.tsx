import { type FormEvent, useEffect, useState } from "react";
import { Link, Navigate, Outlet, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { apiRequest, ApiError } from "../../shared/api/client";
import { useAuth } from "../auth/AuthContext";
import { formatEventTime, readEventQuery } from "./events";
import { fromSingaporeInput, managementError, toSingaporeInput, type ManagedEvent, type ManagedPage } from "./drafts";

const API = "/api/v1/organizer/events";

export function OrganizerRoute() {
  const { user } = useAuth();
  return user?.roles.some((role) => role === "ORGANIZER" || role === "ADMIN")
    ? <Outlet /> : <Navigate to="/" replace />;
}

function useManagementRequest<T>(path: string) {
  const { token } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ path: string; token: string; data?: T; error?: unknown } | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    if (token) apiRequest<T>(path, { signal: controller.signal }, token)
      .then((data) => { if (!controller.signal.aborted) setResult({ path, token, data }); })
      .catch((error: unknown) => { if (!controller.signal.aborted) setResult({ path, token, error }); });
    return () => controller.abort();
  }, [path, token, attempt]);
  const current = result?.path === path && result.token === token ? result : null;
  return { data: current?.data, error: current?.error, loading: !current,
    reload: () => { setResult(null); setAttempt((value) => value + 1); } };
}

function ManagementError({ error }: { error: unknown }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  return <div role="alert">
    <p className="error-message">{managementError(error)}</p>
    {error instanceof ApiError && [401, 403].includes(error.status) &&
      <button type="button" className="secondary-button" onClick={() => { logout(); navigate("/login"); }}>Sign in again</button>}
  </div>;
}

export function MyEventsPage() {
  const [params, setParams] = useSearchParams();
  const { page } = readEventQuery(params);
  const request = useManagementRequest<ManagedPage>(`${API}?page=${page}&size=10`);
  return <main className="app-shell">
    <Link className="text-link" to="/">← My dashboard</Link>
    <header className="app-header organizer-header">
      <div><p className="eyebrow">Organizer workspace</p><h1>My events</h1><p>Prepare drafts, publish events, and manage cancellations.</p></div>
      <Link className="primary-button compact button-link" to="/organizer/events/new">Create event</Link>
    </header>
    {request.loading && <p role="status">Loading your events…</p>}
    {request.error != null && <section className="card"><ManagementError error={request.error} /><button className="secondary-button" onClick={request.reload}>Try again</button></section>}
    {request.data && <>
      <p className="muted" role="status">{request.data.totalElements} events</p>
      {request.data.items.length === 0 ? <section className="card event-feedback"><h2>{page ? "No events on this page" : "Your first event starts here"}</h2><p>Create a draft to start planning. Drafts are visible only to you.</p>
        {page > 0 && <button className="secondary-button" onClick={() => setParams({ page: "0" })}>Back to first page</button>}</section>
        : <div className="event-grid">{request.data.items.map((event) => <article className="card event-card" key={event.id}>
          <span className={`event-status status-${event.status.toLowerCase()}`}>{event.status}</span>
          <h2><Link className="text-link" to={`/organizer/events/${event.id}`}>{event.title}</Link></h2>
          <p>{formatEventTime(event.startsAt)} · SGT</p><p>{event.location}</p>
          <Link className="text-link" to={`/organizer/events/${event.id}`}>{event.status === "DRAFT" ? "Edit draft →" : "View event →"}</Link>
        </article>)}</div>}
      {request.data.items.length > 0 && <nav className="event-pagination" aria-label="My event pages">
        <button className="secondary-button" disabled={page === 0} onClick={() => setParams({ page: String(page - 1) })}>Previous</button>
        <span>Page {page + 1} of {request.data.totalPages}</span>
        <button className="secondary-button" disabled={page + 1 >= request.data.totalPages} onClick={() => setParams({ page: String(page + 1) })}>Next</button>
      </nav>}
    </>}
  </main>;
}

export function EditEventPage() {
  const { id } = useParams();
  const request = useManagementRequest<ManagedEvent>(`${API}/${encodeURIComponent(id ?? "")}`);
  if (request.loading) return <main className="app-shell"><p role="status">Loading event…</p></main>;
  if (request.error != null) return <main className="app-shell"><Link className="text-link" to="/organizer/events">← My events</Link><ManagementError error={request.error} /><button className="secondary-button" onClick={request.reload}>Try again</button></main>;
  return request.data ? <DraftForm key={`${request.data.id}-${request.data.version}`} event={request.data} reload={request.reload} /> : null;
}

export function NewEventPage() { return <DraftForm />; }

function DraftForm({ event, reload }: { event?: ManagedEvent; reload?: () => void }) {
  const { token } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState({ title: event?.title ?? "", description: event?.description ?? "", location: event?.location ?? "",
    startsAt: event ? toSingaporeInput(event.startsAt) : "", endsAt: event ? toSingaporeInput(event.endsAt) : "", capacity: event ? String(event.capacity) : "" });
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [action, setAction] = useState<"publish" | "cancel" | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(Boolean(location.state?.saved));
  const readOnly = !!event && event.status !== "DRAFT";
  function change(name: keyof typeof values, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
    setFields((current) => ({ ...current, [name]: "" }));
    setSaved(false);
    setDirty(true);
    setAction(null);
  }
  async function submit(form: FormEvent) {
    form.preventDefault();
    if (saving || readOnly || !token) return;
    const errors: Record<string, string> = {};
    for (const name of ["title", "description", "location"] as const) if (!values[name].trim()) errors[name] = "This field is required.";
    if (values.endsAt <= values.startsAt) errors.endsAt = "End time must be after start time.";
    if (!Number.isInteger(Number(values.capacity)) || Number(values.capacity) < 1 || Number(values.capacity) > 2147483647) errors.capacity = "Enter a positive whole number up to 2147483647.";
    setFields(errors);
    if (Object.keys(errors).length) return;
    setSaving(true); setError(null); setSaved(false);
    try {
      const body = { ...values, title: values.title.trim(), description: values.description.trim(), location: values.location.trim(),
        startsAt: fromSingaporeInput(values.startsAt), endsAt: fromSingaporeInput(values.endsAt), capacity: Number(values.capacity), ...(event ? { version: event.version } : {}) };
      const result = await apiRequest<ManagedEvent>(event ? `${API}/${event.id}` : API, { method: event ? "PUT" : "POST", body: JSON.stringify(body) }, token);
      navigate(`/organizer/events/${result.id}`, { replace: true, state: { saved: true } });
      if (reload) reload();
    } catch (caught) {
      setError(caught);
      if (caught instanceof ApiError) setFields(caught.problem.fieldErrors ?? {});
    } finally { setSaving(false); }
  }
  async function transition() {
    if (!event || !token || !action || saving || dirty) return;
    setSaving(true); setError(null); setSaved(false);
    try {
      await apiRequest<ManagedEvent>(`${API}/${event.id}/${action}`, {
        method: "POST", body: JSON.stringify({ version: event.version }),
      }, token);
      navigate(`/organizer/events/${event.id}`, { replace: true, state: {
        message: action === "publish" ? "Event published. It is now visible to everyone." : "Event cancelled. It is no longer publicly visible.",
      } });
      reload?.();
    } catch (caught) { setError(caught); setAction(null); }
    finally { setSaving(false); }
  }
  const blocked = saving || (error instanceof ApiError && [401, 403, 404, 409].includes(error.status));
  function fieldError(name: string) {
    return fields[name] ? <span className="field-error" id={`${name}-error`}>{fields[name]}</span> : null;
  }
  return <main className="app-shell draft-shell">
    <Link className="text-link" to="/organizer/events">← My events</Link>
    <header className="organizer-header"><p className="eyebrow">{event?.status ?? "New draft"}</p><h1>{readOnly ? "Event details" : event ? "Edit event draft" : "Create an event"}</h1>
      <p>{readOnly ? "Only draft events can be edited." : "Save your plans privately. This does not publish the event."}</p></header>
    {saved && <p className="result-message" role="status">Draft saved successfully.</p>}
    {location.state?.message && <p className="result-message" role="status">{location.state.message}</p>}
    {event?.status === "PUBLISHED" && <p><Link className="text-link" to={`/events/${event.id}`}>View public event →</Link></p>}
    {event?.status === "CANCELLED" && <p className="result-message">This event is cancelled and cannot be restored.</p>}
    <form className="card draft-form" onSubmit={submit}>
      <fieldset disabled={saving || readOnly}>
        <legend>Event information</legend>
        {([ ["title", "Event title", 200], ["location", "Location", 500] ] as const).map(([name, label, max]) => <label key={name}>{label}
          <input required maxLength={max} value={values[name]} onChange={(e) => change(name, e.target.value)} aria-invalid={!!fields[name]} aria-describedby={fields[name] ? `${name}-error` : undefined} />{fieldError(name)}</label>)}
        <label>Description<textarea required maxLength={10000} rows={7} value={values.description} onChange={(e) => change("description", e.target.value)} aria-invalid={!!fields.description} aria-describedby={fields.description ? "description-error" : undefined} />{fieldError("description")}</label>
        <p className="muted" id="event-time-zone">All times use Singapore time (SGT, UTC+08:00).</p>
        <div className="profile-grid">{([ ["startsAt", "Start time"], ["endsAt", "End time"] ] as const).map(([name, label]) => <label key={name}>{label}
          <input type="datetime-local" step="1" required value={values[name]} onChange={(e) => change(name, e.target.value)} aria-invalid={!!fields[name]} aria-describedby={`event-time-zone${fields[name] ? ` ${name}-error` : ""}`} />{fieldError(name)}</label>)}</div>
        <label>Capacity<input type="number" min="1" max="2147483647" step="1" required value={values.capacity} onChange={(e) => change("capacity", e.target.value)} aria-invalid={!!fields.capacity} aria-describedby={fields.capacity ? "capacity-error capacity-help" : "capacity-help"} />{fieldError("capacity")}</label>
        <p className="muted" id="capacity-help">Event size only; this does not create ticket inventory.</p>
      </fieldset>
      {error != null && <ManagementError error={error} />}
      {!readOnly && <div className="button-row">
        <button type="submit" className="primary-button compact" disabled={saving || (error instanceof ApiError && [401, 403, 404, 409].includes(error.status))}>{saving ? "Saving…" : "Save draft"}</button>

      </div>}
      {reload && error instanceof ApiError && error.status === 409 && <button type="button" className="secondary-button" onClick={reload}>Reload latest version</button>}
    </form>
    {event && event.status !== "CANCELLED" && <section className="card event-actions" aria-label="Event actions">
      <h2>Event actions</h2>
      {dirty && <p role="status">Save your changes before publishing or cancelling.</p>}
      <div className="button-row">
        {event.status === "DRAFT" && <button className="primary-button compact" disabled={blocked || dirty} onClick={() => setAction("publish")}>Publish event</button>}
        <button className="secondary-button danger-button" disabled={blocked || dirty} onClick={() => setAction("cancel")}>Cancel event</button>
      </div>
      {action && <div className="action-confirmation" role="group" aria-label="Confirm event action">
        <p>{action === "publish" ? "Publish this saved draft? Everyone will be able to view it, and its details can no longer be edited." : "Cancel this event? It will be removed from public browsing. This cannot be undone."}</p>
        <div className="button-row"><button className="secondary-button" disabled={saving} onClick={() => setAction(null)}>Go back</button>
          <button className={action === "cancel" ? "secondary-button danger-button" : "primary-button compact"} disabled={saving} onClick={transition}>{saving ? "Updating…" : action === "publish" ? "Confirm publication" : "Confirm cancellation"}</button></div>
      </div>}
    </section>}
  </main>;
}
