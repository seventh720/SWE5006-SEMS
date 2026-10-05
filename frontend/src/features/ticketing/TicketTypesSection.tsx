import { useEffect, useState } from "react";
import { ReserveControl } from "../bookings/ReserveControl";
import { readPublicTicketTypes } from "./ticketTypesApi";
import {
  formatSgdPrice,
  isFree,
  isSoldOut,
  publicTicketTypesPath,
  remainingFor,
  type TicketType,
} from "./ticketTypes";

export function usePublicTicketTypes(eventId: string) {
  const path = publicTicketTypesPath(eventId);
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ path: string; data?: TicketType[]; error?: string } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    readPublicTicketTypes(eventId, controller.signal)
      .then((data) => { if (!controller.signal.aborted) setResult({ path, data }); })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setResult({ path, error: "We couldn't load ticket types right now. Please try again." });
      });
    return () => controller.abort();
  }, [path, attempt]);

  const current = result?.path === path ? result : null;
  return {
    data: current?.data,
    error: current?.error,
    loading: !current,
    // Re-fetch without wiping the current view, so a post-booking availability
    // refresh (and an error retry) do not flash back to a loading state.
    refresh: () => { setAttempt((value) => value + 1); },
  };
}

export function TicketTypesSection({ eventId }: { eventId: string }) {
  const request = usePublicTicketTypes(eventId);
  return <section className="card ticket-types" aria-label="Ticket types" aria-busy={request.loading}>
    <h2>Ticket types</h2>
    {request.loading && <p role="status">Loading ticket types…</p>}
    {request.error && <div role="alert">
      <p className="error-message">{request.error}</p>
      <button className="secondary-button" onClick={request.refresh}>Try again</button>
    </div>}
    {request.data && request.data.length === 0 && <p className="muted">Booking is not available yet.</p>}
    {request.data && request.data.length > 0 && <ul className="ticket-type-list">
      {request.data.map((ticketType) => {
        const remaining = remainingFor(ticketType);
        const reservable = isFree(ticketType) && !isSoldOut(ticketType);
        return <li key={ticketType.id} className="ticket-type-row">
          <div className="ticket-type-info">
            <span className="ticket-type-name">{ticketType.name}</span>
            <span className={isFree(ticketType) ? "ticket-badge badge-free" : "ticket-badge badge-paid"}>
              {isFree(ticketType) ? "Free" : "Paid"}
            </span>
            <span className="ticket-type-price">{formatSgdPrice(ticketType.priceMinor)}</span>
          </div>
          <div className="ticket-type-meta">
            {isSoldOut(ticketType)
              ? <span className="ticket-type-sold-out">Sold out</span>
              : <span>{remaining} of {ticketType.quota} available</span>}
            {!isFree(ticketType) && <span className="muted">Booking for paid tickets is not available yet.</span>}
          </div>
          {reservable && <ReserveControl eventId={eventId} ticketTypeId={ticketType.id} remaining={remaining} onBooked={request.refresh} />}
        </li>;
      })}
    </ul>}
  </section>;
}
