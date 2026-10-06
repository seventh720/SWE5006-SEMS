import { Link, useParams, useSearchParams } from "react-router-dom";
import { ManagementError, useManagementRequest } from "../events/OrganizerPages";
import { formatEventTime } from "../events/events";
import { AttendeeInfoSummary } from "./AttendeeInfoSummary";
import { BookingStatusBadge } from "./BookingStatusBadge";
import { readBookingPage, type BookingStatus } from "./bookings";

import type { AttendeeInfo } from "./attendeeInfo";

interface OrganizerOrder {
  attendeeInfo?: AttendeeInfo;
  customFieldLabel?: string | null;
  id: string;
  attendeeId: string;
  ticketTypeName: string;
  quantity: number;
  status: BookingStatus;
  cancellationReason?: string;
  createdAt: string;
}
interface OrganizerOrderPage {
  items: OrganizerOrder[];
  totalElements: number;
  totalPages: number;
}

export function OrganizerOrdersPage() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const page = readBookingPage(params);
  const eventId = encodeURIComponent(id ?? "");
  const request = useManagementRequest<OrganizerOrderPage>(`/api/v1/organizer/events/${eventId}/bookings?page=${page}&size=10`);
  return <main className="app-shell">
    <Link className="text-link" to={`/organizer/events/${eventId}`}>← Back to event</Link>
    <header className="organizer-header"><p className="eyebrow">Organizer workspace</p><h1>Event orders</h1></header>
    {request.loading && <p role="status">Loading event orders…</p>}
    {request.error != null && <section className="card"><ManagementError error={request.error} />
      <button className="secondary-button" onClick={request.reload}>Try again</button></section>}
    {request.data && <>
      <p role="status">{request.data.totalElements} orders</p>
      {request.data.items.length === 0 ? <section className="card"><h2>No orders on this page</h2>
        {page > 0 && <button className="secondary-button" onClick={() => setParams({ page: "0" })}>Back to first page</button>}
      </section> : <ul className="ticket-type-list">{request.data.items.map((order) => <li className="ticket-type-row" key={order.id}>
        <div className="ticket-type-info"><strong>Order {order.id}</strong><BookingStatusBadge status={order.status} /></div>
        <div className="ticket-type-meta"><span>Attendee: {order.attendeeId}</span><span>{order.ticketTypeName} · {order.quantity} tickets</span>
          <span>{formatEventTime(order.createdAt)} · SGT</span>
          {order.cancellationReason && <span>{order.cancellationReason === "EVENT_CANCELLED" ? "Event cancelled" : "Cancelled by attendee"}</span>}
        </div>
        <AttendeeInfoSummary customFieldLabel={order.customFieldLabel} info={order.attendeeInfo} />
      </li>)}</ul>}
      {request.data.items.length > 0 && <nav className="event-pagination" aria-label="Event order pages">
        <button className="secondary-button" disabled={page === 0} onClick={() => setParams({ page: String(page - 1) })}>Previous</button>
        <span>Page {page + 1} of {request.data.totalPages}</span>
        <button className="secondary-button" disabled={page + 1 >= request.data.totalPages} onClick={() => setParams({ page: String(page + 1) })}>Next</button>
      </nav>}
    </>}
  </main>;
}
