import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import {
  classifyReservationError,
  newIdempotencyKey,
  validateBookingQuantity,
  type Booking,
  type ReservationFailure,
} from "./bookings";
import { createBooking } from "./bookingsApi";

export function ReserveControl({ eventId, ticketTypeId, remaining, onBooked }: {
  eventId: string;
  ticketTypeId: string;
  remaining: number;
  onBooked: () => void;
}) {
  const { user, token, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [quantity, setQuantity] = useState("1");
  const [requestKey, setRequestKey] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [booking, setBooking] = useState<Booking | null>(null);
  const [failure, setFailure] = useState<ReservationFailure | null>(null);

  const quantityError = validateBookingQuantity(quantity, remaining);

  function changeQuantity(value: string) {
    setQuantity(value);
    setRequestKey(null);
    setFailure(null);
  }

  function startNewReservation() {
    setBooking(null);
    setQuantity("1");
    setRequestKey(null);
    setFailure(null);
  }

  async function submit() {
    if (!user) {
      navigate("/login", { state: { from: location } });
      return;
    }
    if (!user.roles.includes("ATTENDEE")) {
      setFailure({ kind: "forbidden", message: "You need an attendee role to book tickets.", retryable: false });
      return;
    }
    if (!token || quantityError || submitting) return;
    setSubmitting(true);
    setFailure(null);
    try {
      const idempotencyKey = requestKey ?? newIdempotencyKey();
      setRequestKey(idempotencyKey);
      const result = await createBooking(
        { eventId, ticketTypeId, quantity: Number(quantity) },
        token,
        idempotencyKey,
      );
      setBooking(result);
      setQuantity("1");
      setRequestKey(null);
      onBooked();
    } catch (caught) {
      const classified = classifyReservationError(caught);
      if (classified.retryable) {
        setFailure(classified); // keep requestKey for a same-key retry
      } else {
        setRequestKey(null);
        setFailure(classified);
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (booking) {
    return <div className="reserve-confirmation result-message">
      <div role="status">
        <p>Reservation confirmed.</p>
        <p>Reservation number: <strong>{booking.id}</strong></p>
        <p className="muted">Electronic tickets are not issued yet — they will be available in a later release.</p>
      </div>
      <div className="button-row">
        <Link className="primary-button compact button-link" to="/bookings">View my orders</Link>
        <button type="button" className="secondary-button" onClick={startNewReservation}>Reserve more</button>
      </div>
    </div>;
  }

  return <div className="reserve-control">
    <label className="reserve-quantity">Quantity
      <input type="number" min="1" max={remaining} step="1" inputMode="numeric" value={quantity}
        onChange={(event) => changeQuantity(event.target.value)}
        aria-invalid={!!quantityError}
        aria-describedby={quantityError ? `${ticketTypeId}-quantity-error` : undefined} />
    </label>
    {quantityError && <p className="field-error" id={`${ticketTypeId}-quantity-error`}>{quantityError}</p>}
    {failure && <div role="alert">
      <p className="error-message">{failure.message}</p>
      {failure.kind === "auth" &&
        <button type="button" className="secondary-button"
          onClick={() => { logout(); navigate("/login", { state: { from: location } }); }}>Sign in again</button>}
    </div>}
    <button type="button" className="primary-button compact" disabled={submitting || !!quantityError} onClick={submit}>
      {submitting ? "Reserving…" : requestKey ? "Retry reservation" : "Reserve"}
    </button>
  </div>;
}
