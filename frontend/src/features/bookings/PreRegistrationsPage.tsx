import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiRequest, ApiError } from "../../shared/api/client";
import { useAuth } from "../auth/AuthContext";
import { formatEventTime } from "../events/events";
import { readBookingPage } from "./bookings";
import { openingCountdown, useRegistrationClock } from "./registrationTime";
import { preRegistrationPath, preRegistrationStatus, type PreRegistrationPage } from "./preRegistrations";

export function PreRegistrationsPanel({ compact = false }: { compact?: boolean }) {
  const { token, logout } = useAuth();
  const [params, setParams] = useSearchParams();
  const page = compact ? 0 : readBookingPage(params);
  const [result, setResult] = useState<{ path: string; token: string; data?: PreRegistrationPage; error?: unknown } | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [removing, setRemoving] = useState(false);
  const [actionError, setActionError] = useState("");
  const now = useRegistrationClock();
  const path = `/api/v1/pre-registrations?page=${page}&size=${compact ? 3 : 10}`;
  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    if (token) apiRequest<PreRegistrationPage>(path, { signal: controller.signal }, token)
      .then((data) => { if (!controller.signal.aborted) setResult({ path, token, data }); })
      .catch((error: unknown) => { if (!controller.signal.aborted) setResult({ path, token, error }); });
    return () => controller.abort();
  }, [path, token, attempt]);
  const current = result?.path === path && result.token === token ? result : null;
  const expired = current?.error instanceof ApiError && current.error.status === 401;
  async function remove(eventId: string) {
    if (!token || removing) return;
    setRemoving(true); setActionError("");
    try {
      await apiRequest<void>(preRegistrationPath(eventId), { method: "DELETE" }, token);
      setAttempt((value) => value + 1);
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : "We couldn't remove this pre-registration. Please try again.");
    } finally { setRemoving(false); }
  }
  return <section aria-label="My pre-registrations">
    <div className="event-results-heading"><h2>My pre-registrations</h2>
      {compact ? <Link className="text-link" to="/pre-registrations">View all →</Link>
        : <button className="secondary-button" onClick={() => setAttempt((value) => value + 1)}>Refresh</button>}
    </div>
    <p className="muted">Pre-registration saves your details only. It does not hold tickets or submit a booking automatically. Confirm your booking after registration opens.</p>
    {!current && <p role="status">Loading pre-registrations…</p>}
    {current?.error != null && <div role="alert"><p className="error-message">{expired ? "Your session has expired. Please sign in again." : "We couldn't load your pre-registrations."}</p>
      <button className="secondary-button" onClick={expired ? logout : () => setAttempt((value) => value + 1)}>{expired ? "Sign in again" : "Try again"}</button></div>}
    {current?.data && <>
      {current.data.items.length === 0 ? <p className="card">No pre-registrations on this page.</p>
        : <div className="event-grid">{current.data.items.map((item) => {
          const status = preRegistrationStatus(item, now);
          return <article className="card" key={item.id}>
            <h3>{item.eventTitle}</h3><p>{item.ticketTypeName} · {item.quantity} tickets requested</p>
            {status === "WAITING" && item.registrationOpensAt && <>
              <p>Registration opens {formatEventTime(item.registrationOpensAt)} · SGT</p>
              <p className="registration-countdown" aria-label="Countdown to registration">Opens in {openingCountdown(item.registrationOpensAt, now)}</p>
            </>}
            {status === "OPEN" && <p className="result-message">Registration is open. Your tickets are not yet booked.</p>}
            {status === "CLOSED" && <p>Registration has closed.</p>}
            {status === "EVENT_CANCELLED" && <p>This event was cancelled.</p>}
            {status === "BOOKED" && <p>A booking was created. Check My orders for its current status.</p>}
            <div className="button-row">
              {(status === "WAITING" || status === "OPEN") && <Link className="primary-button compact button-link"
                to={`/events/${encodeURIComponent(item.eventId)}?preRegistration=${encodeURIComponent(item.ticketTypeId)}`}>{status === "WAITING" ? "Edit pre-registration" : "Review and book"}</Link>}
              {status === "BOOKED" && item.bookingId && <Link className="text-link" to={`/bookings/${item.bookingId}`}>View order →</Link>}
              {!compact && <button className="secondary-button" disabled={removing} onClick={() => remove(item.eventId)}>Remove saved entry</button>}
            </div>
          </article>;
        })}</div>}
      {!compact && (current.data.totalPages > 1 || page > 0) && <nav className="event-pagination" aria-label="Pre-registration pages">
        <button className="secondary-button" disabled={page === 0} onClick={() => setParams({ page: String(page - 1) })}>Previous</button>
        <span>Page {page + 1}</span>
        <button className="secondary-button" disabled={page + 1 >= current.data.totalPages} onClick={() => setParams({ page: String(page + 1) })}>Next</button>
      </nav>}
    </>}
    {actionError && <p className="error-message" role="alert">{actionError}</p>}
    {!compact && <p className="muted">Removing a saved entry does not cancel an existing booking.</p>}
  </section>;
}

export function PreRegistrationsPage() {
  return <main className="app-shell"><Link className="text-link" to="/">← Dashboard</Link>
    <h1>Pre-registration overview</h1><PreRegistrationsPanel />
  </main>;
}
