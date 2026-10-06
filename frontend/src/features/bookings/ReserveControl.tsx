import { useState, type FormEvent } from "react";
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

import { noBookingRequirements, requiredPersonalFields, type AttendeeInfo, type BookingRequirements } from "./attendeeInfo";

import { PersonalInfoFields } from "../profile/PersonalInfoFields";
import { PrivacyNotice } from "../profile/PrivacyNotice";
import { readProfile } from "../profile/profile";

export function ReserveControl({ eventId, ticketTypeId, remaining, onBooked, closed = false, bookingRequirements = noBookingRequirements }: {
  eventId: string;
  ticketTypeId: string;
  remaining: number;
  onBooked: () => void;
  closed?: boolean;
  bookingRequirements?: BookingRequirements;
}) {
  const { user, token, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [info, setInfo] = useState<AttendeeInfo>({});
  const [applying, setApplying] = useState(false);
  const [profileMessage, setProfileMessage] = useState("");
  const personalFields = requiredPersonalFields(bookingRequirements);
  const [infoError, setInfoError] = useState("");
  const email = info.email ?? user?.email ?? "";
  const needsInfo = Object.values(bookingRequirements).some(Boolean);
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

  async function applySavedDetails() {
    if (!token || applying || submitting || requestKey) return;
    setApplying(true); setProfileMessage("");
    try {
      const profile = await readProfile(token);
      const saved = Object.fromEntries(personalFields.filter((key) => profile[key]).map((key) => [key, profile[key]]));
      setInfo((current) => ({ ...current, ...saved }));
      setProfileMessage(Object.keys(saved).length
        ? "Saved details applied. You can edit them for this booking."
        : "No saved details match this event. You can enter them below or save details in your profile.");
    } catch {
      setProfileMessage("We couldn't load saved details. Please try again or enter your details below.");
    } finally { setApplying(false); }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) {
      navigate("/login", { state: { from: location } });
      return;
    }
    if (!user.roles.includes("ATTENDEE")) {
      setFailure({ kind: "forbidden", message: "You need an attendee role to book tickets.", retryable: false });
      return;
    }
    if (!token || quantityError || submitting || applying || closed) return;
    const attendeeInfo: AttendeeInfo = {
      ...(bookingRequirements.realName ? { realName: info.realName?.trim() ?? "" } : {}),
      ...(bookingRequirements.email ? { email: email.trim() } : {}),
      ...(bookingRequirements.phone ? { phone: info.phone?.trim() ?? "" } : {}),
      ...(bookingRequirements.studentId ? { studentId: info.studentId?.trim() ?? "" } : {}),
      ...(bookingRequirements.passport ? { passportNumber: info.passportNumber?.trim() ?? "" } : {}),
      ...(bookingRequirements.customFieldLabel ? { customAnswer: info.customAnswer?.trim() ?? "" } : {}),
    };
    if (Object.values(attendeeInfo).some((value) => !value)) {
      setInfoError("Complete all booking information requested by the organizer.");
      return;
    }
    setInfoError("");
    setSubmitting(true);
    setFailure(null);
    try {
      const idempotencyKey = requestKey ?? newIdempotencyKey();
      setRequestKey(idempotencyKey);
      const result = await createBooking(
        { eventId, ticketTypeId, quantity: Number(quantity), ...(needsInfo ? { attendeeInfo } : {}) },
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
        <p>{booking.status === "CANCELLED" ? "This reservation was already cancelled." : "Reservation confirmed."}</p>
        <p>Reservation number: <strong>{booking.id}</strong></p>
        <p className="muted">Electronic tickets are not issued yet — they will be available in a later release.</p>
      </div>
      <div className="button-row">
        <Link className="primary-button compact button-link" to="/bookings">View my orders</Link>
        <button type="button" className="secondary-button" disabled={closed || remaining === 0} onClick={startNewReservation}>Reserve more</button>
      </div>
    </div>;
  }

  if (closed || remaining === 0) return null;

  return <form className="reserve-control" onSubmit={submit}>
    {user && needsInfo && <fieldset disabled={submitting || applying || !!requestKey} className="booking-information">
      <legend>Booking information</legend>
      <p className="muted">The organizer requires these details once per order.</p>
      <PrivacyNotice />
      {personalFields.length > 0 && <div className="button-row">
        <button type="button" className="secondary-button" disabled={applying} onClick={applySavedDetails}>{applying ? "Applying…" : "Apply saved details"}</button>
        <Link className="text-link" to="/profile">Manage saved details</Link>
      </div>}
      {profileMessage && <p role="status">{profileMessage}</p>}
      <PersonalInfoFields required fields={personalFields} value={{ ...info, email }} onChange={(value) => setInfo({ ...info, ...value })} />
      {bookingRequirements.customFieldLabel && <label>{bookingRequirements.customFieldLabel}
        <input required maxLength={500} value={info.customAnswer ?? ""} onChange={(event) => setInfo({ ...info, customAnswer: event.target.value })} />
      </label>}
    </fieldset>}
    {infoError && <p className="error-message" role="alert">{infoError}</p>}
    <label className="reserve-quantity">Quantity
      <input disabled={submitting || applying || !!requestKey} type="number" min="1" max={Math.min(10, remaining)} step="1" inputMode="numeric" value={quantity}
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
    <button type="submit" className="primary-button compact" disabled={submitting || applying || !!quantityError}>
      {submitting ? "Reserving…" : requestKey ? "Retry reservation" : "Reserve"}
    </button>
  </form>;
}
