import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../../shared/api/client";
import { formatEventTime } from "../events/events";
import { markNotificationRead, notificationDestination, notificationLabels, notificationsEnabled,
  readNotifications, type NotificationPage } from "./notifications";

function useNotifications(page = 0, unread = false) {
  const { token } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ key: string; data?: NotificationPage; error?: unknown } | null>(null);
  const key = `${token}:${page}:${unread}:${attempt}`;
  useEffect(() => {
    if (!notificationsEnabled || !token) return;
    const refresh = () => setAttempt(value => value + 1);
    const timer = window.setInterval(refresh, 30000);
    window.addEventListener("focus", refresh);
    return () => { window.clearInterval(timer); window.removeEventListener("focus", refresh); };
  }, [token]);
  useEffect(() => {
    if (!notificationsEnabled || !token) return;
    const controller = new AbortController();
    readNotifications(token, page, unread, controller.signal)
      .then(data => { if (!controller.signal.aborted) setResult({ key, data }); })
      .catch((error: unknown) => { if (!controller.signal.aborted) setResult({ key, error }); });
    return () => controller.abort();
  }, [token, page, unread, key]);
  const current = result?.key === key ? result : null;
  return { data: current?.data, error: current?.error, loading: notificationsEnabled && !current,
    reload: () => setAttempt(value => value + 1) };
}

export function NotificationEntry() {
  const { data } = useNotifications();
  return <Link className="secondary-button button-link notification-entry" to="/notifications"
    aria-label={data ? `Notifications, ${data.unreadCount} unread` : "Notifications"}>
    <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M9 21h6" />
    </svg>
    Notifications
    {data && data.unreadCount > 0 && <span className="notification-count" aria-hidden="true">{data.unreadCount > 99 ? "99+" : data.unreadCount}</span>}
  </Link>;
}

export function NotificationsPage() {
  const { token, logout } = useAuth();
  const [unread, setUnread] = useState(false);
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);
  const request = useNotifications(page, unread);
  const error = request.error ?? actionError;
  const authError = error instanceof ApiError && (error.status === 401 || error.status === 403);

  async function markRead(id: string) {
    if (!token || busy) return;
    setBusy(id); setActionError(null);
    try {
      await markNotificationRead(token, id);
      if (unread && request.data?.items.length === 1 && page > 0) setPage(page - 1);
      request.reload();
    } catch (failure) { setActionError(failure); }
    finally { setBusy(null); }
  }

  return <main className="app-shell notifications-shell">
    <Link className="text-link" to="/">← My dashboard</Link>
    <header className="app-header organizer-header"><div><p className="eyebrow">Your updates</p><h1>Notifications</h1>
      <p>Booking updates, event changes, registration reminders, and staff assignments.</p></div>
      {request.data && <span className="role-chip">{request.data.unreadCount} unread</span>}
    </header>
    {!notificationsEnabled ? <>
      <section className="card"><span className="event-status status-draft">Coming soon</span><h2>Your updates, in one place</h2>
        <p>Notifications are not available yet. Registration reminders are not being sent.</p>
        <p className="muted">For now, check your orders and pre-registrations directly. Ask your organizer about staff assignments.</p>
      </section>
      <section className="notification-categories" aria-label="Upcoming notification types">
        <article className="card"><p className="section-label">Event staff</p><h2>Assignment updates</h2><p>Know when an organizer assigns you to an event or removes your access.</p></article>
        <article className="card"><p className="section-label">Your tickets</p><h2>Booking updates</h2><p>Confirmation when a booking succeeds, and updates when an order or event is cancelled.</p></article>
        <article className="card"><p className="section-label">Pre-registrations</p><h2>5-minute reminder</h2><p>A reminder five minutes before registration opens for an event you pre-registered for.</p><p className="muted">You still need to confirm your booking. Pre-registration does not reserve tickets.</p></article>
        <article className="card"><p className="section-label">Plans changed</p><h2>Event updates</h2><p>Changes to the event time, location, and other important arrangements.</p></article>
      </section>
    </> : <>
      <div className="notification-toolbar"><div className="dashboard-views" aria-label="Notification filter">
        {[false, true].map(value => <button className="secondary-button" key={String(value)} aria-pressed={unread === value}
          disabled={busy !== null} onClick={() => { setUnread(value); setPage(0); setActionError(null); }}>{value ? "Unread" : "All"}</button>)}
      </div><button className="secondary-button" disabled={request.loading || busy !== null} onClick={request.reload}>Refresh</button></div>
      {request.loading && <p role="status">Loading notifications…</p>}
      {error != null && <section className="card"><p role="alert" className="error-message">{authError ? "Please sign in again to view your notifications." : "We couldn't update your notifications. Please try again."}</p>
        <button className="secondary-button" onClick={authError ? logout : () => { setActionError(null); request.reload(); }}>{authError ? "Sign in again" : "Try again"}</button></section>}
      {request.data && <>
        {request.data.items.length === 0 ? <section className="card"><h2>{unread ? "You're all caught up" : "No notifications yet"}</h2><p>New updates will appear here.</p></section> :
          <ul className="notification-list">{request.data.items.map(item => {
            const destination = notificationDestination(item);
            return <li key={item.id} className={`card notification-item${item.readAt ? "" : " is-unread"}`}>
              <div className="notification-meta"><span className="section-label">{notificationLabels[item.type] ?? "Update"}</span>
                <time dateTime={item.createdAt}>{formatEventTime(item.createdAt)} · SGT</time>{!item.readAt && <span className="role-chip">Unread</span>}</div>
              <h2>{item.title}</h2><p className="notification-message">{item.message}</p><div className="button-row">
                {destination && <Link className="text-link" to={destination.to}>{destination.label} →</Link>}
                {!item.readAt && <button className="secondary-button" disabled={busy !== null} onClick={() => void markRead(item.id)}>{busy === item.id ? "Saving…" : "Mark as read"}</button>}
              </div>
            </li>;
          })}</ul>}
        {(request.data.totalPages > 1 || page > 0) && <nav className="event-pagination" aria-label="Notification pages">
          <button className="secondary-button" disabled={page === 0 || busy !== null} onClick={() => setPage(page - 1)}>Previous</button>
          <span>Page {page + 1}</span><button className="secondary-button" disabled={page + 1 >= request.data.totalPages || busy !== null} onClick={() => setPage(page + 1)}>Next</button>
        </nav>}
      </>}
    </>}
  </main>;
}
