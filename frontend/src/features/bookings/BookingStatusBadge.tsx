import { type BookingStatus } from "./bookings";

export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  return status === "CANCELLED"
    ? <span className="ticket-badge badge-cancelled">Cancelled</span>
    : <span className="ticket-badge badge-confirmed">Confirmed</span>;
}
