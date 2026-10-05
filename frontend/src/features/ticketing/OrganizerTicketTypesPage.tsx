import { type FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../../shared/api/client";
import { useAuth } from "../auth/AuthContext";
import { createTicketType, readOrganizerTicketTypes, updateTicketType } from "./ticketTypesApi";
import {
  formatSgdPrice,
  isManagedTicketType,
  isSoldOut,
  minorToSgdInput,
  organizerTicketTypesPath,
  parseSgdPriceToMinor,
  remainingFor,
  SGD_CURRENCY,
  type ManagedTicketType,
  type TicketType,
} from "./ticketTypes";

function ticketTypeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "An organizer or administrator role is required. Sign in again after your role is updated.";
    if (error.status === 404) return "This event is unavailable or does not belong to your account.";
    if (error.status === 409) return "This ticket type has changed or can no longer be modified. Reload the latest details before trying again.";
    if (error.status === 400) return error.message;
  }
  return "We couldn't complete the request. Please try again.";
}

function useOrganizerTicketTypes(eventId: string) {
  const { token } = useAuth();
  const path = organizerTicketTypesPath(eventId);
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ path: string; token: string; data?: TicketType[]; error?: unknown } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    if (token) readOrganizerTicketTypes(eventId, token, controller.signal)
      .then((data) => { if (!controller.signal.aborted) setResult({ path, token, data }); })
      .catch((error: unknown) => { if (!controller.signal.aborted) setResult({ path, token, error }); });
    return () => controller.abort();
  }, [path, token, attempt]);

  const current = result?.path === path && result.token === token ? result : null;
  return {
    data: current?.data,
    error: current?.error,
    loading: !current,
    reload: () => { setResult(null); setAttempt((value) => value + 1); },
  };
}

export function OrganizerTicketTypesPage() {
  const { id } = useParams();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const eventId = id ?? "";
  const request = useOrganizerTicketTypes(eventId);
  const [editing, setEditing] = useState<ManagedTicketType | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  return <main className="app-shell draft-shell">
    <div className="ticket-types-nav">
      <Link className="text-link" to="/organizer/events">← My events</Link>
      <Link className="text-link" to={`/organizer/events/${encodeURIComponent(eventId)}`}>← Back to event</Link>
    </div>
    <header className="organizer-header">
      <p className="eyebrow">Organizer workspace</p>
      <h1>Ticket types</h1>
      <p>Configure ticket names, prices and quotas. Total ticket quota cannot exceed the event capacity, and tickets become read-only once bookings begin.</p>
    </header>
    {notice && <p className="result-message" role="status">{notice}</p>}
    {request.loading && <p role="status">Loading ticket types…</p>}
    {request.error != null && <section className="card">
      <div role="alert">
        <p className="error-message">{ticketTypeError(request.error)}</p>
        {request.error instanceof ApiError && [401, 403].includes(request.error.status) &&
          <button type="button" className="secondary-button" onClick={() => { logout(); navigate("/login"); }}>Sign in again</button>}
      </div>
      <button className="secondary-button" onClick={request.reload}>Try again</button>
    </section>}
    {request.data && <>
      <p className="muted" role="status">{request.data.length === 1 ? "1 ticket type" : `${request.data.length} ticket types`}</p>
      {request.data.length === 0 ? <section className="card event-feedback">
        <h2>No ticket types yet</h2><p>Add a free ticket type so attendees can book this event.</p>
      </section> : <ul className="ticket-type-list">
        {request.data.map((ticketType) => <li key={ticketType.id} className="ticket-type-row">
          <div className="ticket-type-info">
            <span className="ticket-type-name">{ticketType.name}</span>
            {isSoldOut(ticketType) && <span className="ticket-badge badge-sold-out">Sold out</span>}
            <span className="ticket-type-price">{formatSgdPrice(ticketType.priceMinor)} · {remainingFor(ticketType)} of {ticketType.quota} available</span>
          </div>
          {isManagedTicketType(ticketType)
            ? <button type="button" className="secondary-button compact" onClick={() => { setEditing(ticketType); setNotice(null); }}>Edit</button>
            : <span className="muted">Editing unavailable</span>}
        </li>)}
      </ul>}
      <TicketTypeForm key={editing?.id ?? "new"} eventId={eventId} editing={editing} reload={request.reload}
        onSaved={(message) => { setEditing(null); setNotice(message); request.reload(); }}
        onCancelEdit={() => setEditing(null)} />
    </>}
  </main>;
}

function TicketTypeForm({ eventId, editing, reload, onSaved, onCancelEdit }: {
  eventId: string;
  editing: ManagedTicketType | null;
  reload: () => void;
  onSaved: (message: string) => void;
  onCancelEdit: () => void;
}) {
  const { token, logout } = useAuth();
  const navigate = useNavigate();
  const [values, setValues] = useState({
    name: editing?.name ?? "",
    price: editing ? minorToSgdInput(editing.priceMinor) : "",
    quota: editing ? String(editing.quota) : "",
  });
  const [fields, setFields] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);

  function change(name: keyof typeof values, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
    setFields((current) => ({ ...current, [name]: "" }));
    setError(null);
  }

  async function submit(form: FormEvent) {
    form.preventDefault();
    if (saving || !token) return;
    const errors: Record<string, string> = {};
    if (!values.name.trim()) errors.name = "This field is required.";
    const priceMinor = parseSgdPriceToMinor(values.price);
    if (priceMinor == null) errors.price = "Enter a non-negative price in Singapore dollars, e.g. 12.50.";
    const quota = Number(values.quota);
    if (!/^\d+$/.test(values.quota.trim()) || !Number.isSafeInteger(quota) || quota < 1 || quota > 2147483647) {
      errors.quota = "Enter a positive whole number up to 2147483647.";
    }
    setFields(errors);
    if (Object.keys(errors).length) return;
    setSaving(true); setError(null);
    try {
      const payload = { name: values.name.trim(), priceMinor: priceMinor as number, currency: SGD_CURRENCY, quota };
      if (editing) {
        await updateTicketType(eventId, editing.id, token, { ...payload, version: editing.version });
        onSaved("Ticket type updated.");
      } else {
        await createTicketType(eventId, token, payload);
        onSaved("Ticket type created.");
      }
    } catch (caught) {
      setError(caught);
      if (caught instanceof ApiError) {
        const fieldErrors = caught.problem.fieldErrors ?? {};
        setFields({ name: fieldErrors.name ?? "", price: fieldErrors.priceMinor ?? fieldErrors.price ?? "", quota: fieldErrors.quota ?? "" });
      }
    } finally {
      setSaving(false);
    }
  }

  function fieldError(name: string) {
    return fields[name] ? <span className="field-error" id={`${name}-error`}>{fields[name]}</span> : null;
  }

  return <section className="card ticket-type-form">
    <h2>{editing ? "Edit ticket type" : "Add ticket type"}</h2>
    <form onSubmit={submit} noValidate>
      <fieldset disabled={saving}>
        <legend>{editing ? "Update this ticket type" : "Create a new ticket type"}</legend>
        <label>Name
          <input required maxLength={200} value={values.name} onChange={(event) => change("name", event.target.value)}
            aria-invalid={!!fields.name} aria-describedby={fields.name ? "name-error" : undefined} />{fieldError("name")}</label>
        <label>Price (SGD)
          <input type="text" inputMode="decimal" placeholder="0.00" value={values.price} onChange={(event) => change("price", event.target.value)}
            aria-invalid={!!fields.price} aria-describedby={fields.price ? "price-error price-help" : "price-help"} />{fieldError("price")}</label>
        <p className="muted" id="price-help">Enter 0.00 for a free ticket. Amounts are stored in Singapore dollars.</p>
        <label>Quota
          <input type="number" min="1" max="2147483647" step="1" value={values.quota} onChange={(event) => change("quota", event.target.value)}
            aria-invalid={!!fields.quota} aria-describedby={fields.quota ? "quota-error quota-help" : "quota-help"} />{fieldError("quota")}</label>
        <p className="muted" id="quota-help">Number of tickets available. Total quota cannot exceed the event capacity.</p>
      </fieldset>
      {error != null && <div role="alert">
        <p className="error-message">{ticketTypeError(error)}</p>
        {error instanceof ApiError && [401, 403].includes(error.status) &&
          <button type="button" className="secondary-button" onClick={() => { logout(); navigate("/login"); }}>Sign in again</button>}
      </div>}
      <div className="button-row">
        <button type="submit" className="primary-button compact" disabled={saving}>
          {saving ? "Saving…" : editing ? "Save ticket type" : "Create ticket type"}
        </button>
        {editing && <button type="button" className="secondary-button" disabled={saving} onClick={onCancelEdit}>Cancel edit</button>}
      </div>
      {error instanceof ApiError && error.status === 409 &&
        <button type="button" className="secondary-button" onClick={reload}>Reload latest details</button>}
    </form>
  </section>;
}
