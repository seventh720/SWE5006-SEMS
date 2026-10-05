import { useEffect, useState } from "react";
import { Link, Navigate, Outlet, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { formatEventTime } from "../events/events";
import { readBookings } from "./bookingsApi";
import { BookingError } from "./BookingError";
import { formatBookingAmount, readBookingPage, type BookingPage, type BookingStatus } from "./bookings";

export function AttendeeRoute() {
  const { user } = useAuth();
  return user?.roles.includes("ATTENDEE") ? <Outlet /> : <Navigate to="/" replace />;
}

function statusBadge(status: BookingStatus) {
  return status === "CANCELLED"
    ? <span className="ticket-badge badge-cancelled">Cancelled</span>
    : <span className="ticket-badge badge-confirmed">Confirmed</span>;
}

function useBookings(page: number) {
  const { token } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ page: number; token: string; data?: BookingPage; error?: unknown } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    if (token) readBookings(token, page, 10, controller.signal)
      .then((data) => { if (!controller.signal.aborted) setResult({ page, token, data }); })
      .catch((error: unknown) => { if (!controller.signal.aborted) setResult({ page, token, error }); });
    return () => controller.abort();
  }, [page, token, attempt]);

  const current = result?.page === page && result.token === token ? result : null;
  return {
    data: current?.data,
    error: current?.error,
    loading: !current,
    reload: () => { setResult(null); setAttempt((value) => value + 1); },
  };
}

export function MyOrdersPage() {
  const [params, setParams] = useSearchParams();
  const page = readBookingPage(params);
  const request = useBookings(page);

  return <main className="app-shell">
    <Link className="text-link" to="/">← My dashboard</Link>
    <header className="app-header organizer-header">
      <div>
        <p className="eyebrow">My orders</p>
        <h1>My orders</h1>
        <p>Review your reservations and cancel them before the event starts.</p>
      </div>
    </header>
    {request.loading && <p role="status">Loading your orders…</p>}
    {request.error != null && <section className="card">
      <BookingError error={request.error} />
      <button className="secondary-button" onClick={request.reload}>Try again</button>
    </section>}
    {request.data && <>
      <p className="muted" role="status">{request.data.totalElements} order{request.data.totalElements === 1 ? "" : "s"}</p>
      {request.data.items.length === 0
        ? <section className="card event-feedback">
          <h2>No orders yet</h2>
          <p>Reserve a free ticket from an event and it will appear here.</p>
          {page > 0 && <button className="secondary-button" onClick={() => setParams({ page: "0" })}>Back to first page</button>}
        </section>
        : <ul className="ticket-type-list">{request.data.items.map((booking) => <li key={booking.id} className="ticket-type-row">
          <div className="ticket-type-info">
            <span className="ticket-type-name">Order {booking.id}</span>
            {statusBadge(booking.status)}
            <span className="ticket-type-price">{formatBookingAmount(booking.totalAmountMinor)}</span>
          </div>
          <div className="ticket-type-meta">
            <span>{booking.eventTitle}</span>
            <span className="muted">{formatEventTime(booking.eventStartsAt)}</span>
            <span className="muted">{booking.ticketTypeName}</span>
            <span className="muted">{booking.quantity} ticket{booking.quantity === 1 ? "" : "s"}</span>
          </div>
          <Link className="text-link" to={`/bookings/${encodeURIComponent(booking.id)}`}>View order →</Link>
        </li>)}</ul>}
      {request.data.items.length > 0 && <nav className="event-pagination" aria-label="My order pages">
        <button className="secondary-button" disabled={page === 0} onClick={() => setParams({ page: String(page - 1) })}>Previous</button>
        <span>Page {page + 1} of {request.data.totalPages}</span>
        <button className="secondary-button" disabled={page + 1 >= request.data.totalPages} onClick={() => setParams({ page: String(page + 1) })}>Next</button>
      </nav>}
    </>}
  </main>;
}
