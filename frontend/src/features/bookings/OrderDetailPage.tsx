import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../../shared/api/client";
import { useAuth } from "../auth/AuthContext";
import { formatEventTime } from "../events/events";
import { cancelBooking, readBooking } from "./bookingsApi";
import { BookingStatusBadge } from "./BookingStatusBadge";
import { BookingError } from "./BookingError";
import { cancelBookingError, formatBookingAmount, type BookingRecord } from "./bookings";

export function OrderDetailPage() {
  const { id } = useParams();
  const { token, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const bookingId = id ?? "";
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ id: string; token: string; data?: BookingRecord; error?: unknown } | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<unknown>(null);

  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    if (token && bookingId) readBooking(token, bookingId, controller.signal)
      .then((data) => { if (!controller.signal.aborted) setResult({ id: bookingId, token, data }); })
      .catch((error: unknown) => { if (!controller.signal.aborted) setResult({ id: bookingId, token, error }); });
    return () => controller.abort();
  }, [bookingId, token, attempt]);

  const current = result?.id === bookingId && result.token === token ? result : null;
  const booking = current?.data;
  const error = current?.error;
  const loading = !current;

  function reload() { setAttempt((value) => value + 1); }

  async function confirmCancel() {
    if (!booking || !token || cancelling) return;
    setCancelling(true);
    setCancelError(null);
    try {
      const updated = await cancelBooking(token, booking.id);
      setResult({ id: bookingId, token, data: updated });
      setConfirming(false);
    } catch (caught) {
      setCancelError(caught);
      setConfirming(false);
    } finally {
      setCancelling(false);
    }
  }

  return <main className="app-shell">
    <Link className="text-link" to="/bookings">← My orders</Link>
    <header className="organizer-header">
      <p className="eyebrow">My orders</p>
      <h1>Order details</h1>
    </header>
    {loading && <p role="status">Loading order…</p>}
    {error != null && <section className="card">
      <BookingError error={error} />
      <button className="secondary-button" onClick={reload}>Try again</button>
    </section>}
    {booking && <>
      <section className="card">
        <h2>Reservation details</h2>
        <dl className="event-facts">
          <dt>Order number</dt><dd>{booking.id}</dd>
          <dt>Status</dt><dd><BookingStatusBadge status={booking.status} /></dd>
          <dt>Event</dt><dd>{booking.eventTitle}</dd>
          <dt>Location</dt><dd>{booking.eventLocation}</dd>
          <dt>Starts</dt><dd><time dateTime={booking.eventStartsAt}>{formatEventTime(booking.eventStartsAt)}</time></dd>
          <dt>Ends</dt><dd><time dateTime={booking.eventEndsAt}>{formatEventTime(booking.eventEndsAt)}</time></dd>
          <dt>Ticket type</dt><dd>{booking.ticketTypeName}</dd>
          <dt>Quantity</dt><dd>{booking.quantity}</dd>
          <dt>Unit price</dt><dd>{formatBookingAmount(booking.unitPriceMinor)}</dd>
          <dt>Total</dt><dd>{formatBookingAmount(booking.totalAmountMinor)}</dd>
          {booking.cancellationReason && <><dt>Cancellation reason</dt><dd>{booking.cancellationReason}</dd></>}
          {booking.createdAt && <><dt>Reserved</dt><dd><time dateTime={booking.createdAt}>{formatEventTime(booking.createdAt)}</time></dd></>}
        </dl>
      </section>
      {booking.status === "CONFIRMED" && Date.parse(booking.eventStartsAt) > Date.now() && <section className="card event-actions" aria-label="Cancellation">
        <h2>Cancel reservation</h2>
        {!confirming
          ? <button className="secondary-button danger-button" onClick={() => setConfirming(true)}>Cancel reservation</button>
          : <div className="action-confirmation" role="group" aria-label="Confirm cancellation">
            <p>Cancel this reservation? The reserved quantity will be returned to availability.</p>
            <div className="button-row">
              <button className="secondary-button" disabled={cancelling} onClick={() => setConfirming(false)}>Go back</button>
              <button className="secondary-button danger-button" disabled={cancelling} onClick={confirmCancel}>{cancelling ? "Cancelling…" : "Confirm cancellation"}</button>
            </div>
          </div>}
      </section>}
      {cancelError != null && <div role="alert">
        <p className="error-message">{cancelBookingError(cancelError)}</p>
        {cancelError instanceof ApiError && cancelError.status === 401
          ? <button type="button" className="secondary-button" onClick={() => { logout(); navigate("/login", { state: { from: location } }); }}>Sign in again</button>
          : <button type="button" className="secondary-button" onClick={reload}>Reload</button>}
      </div>}
      {booking.status === "CANCELLED" && <p className="result-message" role="status">This reservation has been cancelled.</p>}
    </>}
  </main>;
}
