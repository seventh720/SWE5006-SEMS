import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiRequest } from "../../shared/api/client";
import { readBooking } from "../bookings/bookingsApi";
import type { BookingRecord } from "../bookings/bookings";
import { BookingError } from "../bookings/BookingError";
import { formatEventTime } from "../events/events";

interface Ticket { id: string; ordinal: number; status: "VALID" | "CHECKED_IN" | "VOID"; checkedInAt: string | null }
export function MyTicketsPage() {
  const { id = "" } = useParams();
  const { token } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{id:string; token:string; booking?:BookingRecord; tickets?:Ticket[]; error?:unknown} | null>(null);
  useEffect(() => {
    const refresh = () => setAttempt(value => value + 1);
    const timer = window.setInterval(refresh, 30000);
    window.addEventListener("focus", refresh);
    return () => { window.clearInterval(timer); window.removeEventListener("focus", refresh); };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    if (!token) return;
    Promise.all([readBooking(token, id, controller.signal),
      apiRequest<Ticket[]>(`/api/v1/bookings/${encodeURIComponent(id)}/tickets`, {method:"POST", signal:controller.signal}, token)])
      .then(([booking,tickets]) => { if (!controller.signal.aborted) setResult({id,token,booking,tickets}); })
      .catch(error => { if (!controller.signal.aborted) setResult({id,token,error}); });
    return () => controller.abort();
  }, [id,token,attempt]);
  const current = result?.id === id && result.token === token ? result : null;
  const booking = current?.booking;
  const event = booking?.currentEvent ?? (booking && {title:booking.eventTitle,location:booking.eventLocation,startsAt:booking.eventStartsAt,endsAt:booking.eventEndsAt});
  return <main className="app-shell">
    <Link className="text-link" to={`/bookings/${id}`}>← Order details</Link>
    <header className="app-header"><div><p className="eyebrow">My tickets</p><h1>Electronic tickets</h1>
      <p>Show one QR code per attendee. Each ticket can be checked in once.</p></div></header>
    <button className="secondary-button" onClick={()=>setAttempt(value=>value+1)}>Refresh tickets</button>
    {!current && <p role="status">Loading tickets…</p>}
    {current?.error != null && <BookingError error={current.error}/>}
    {event && <section className="card"><h2>{event.title}</h2><p>{event.location}</p>
      <p>{formatEventTime(event.startsAt)} – {formatEventTime(event.endsAt)}</p>
      <p>Check-in opens 15 minutes before the start and closes 15 minutes after the start, or at the event end if earlier.</p>
      <p className="muted">Keep your ticket private. Staff confirm its current status at the door.</p></section>}
    {current?.tickets?.length === 0 && <p>No tickets are available for this order.</p>}
    <div className="event-grid">{current?.tickets?.map(ticket=><article className="card" key={ticket.id}>
      <h2>Ticket {ticket.ordinal}</h2><p>{ticket.status === "VALID" ? "Valid" : ticket.status === "CHECKED_IN" ? "Checked in" : "Voided"}</p>
      {ticket.status === "VALID" && booking?.status === "CONFIRMED" ? <TicketQr code={`sems:ticket:${ticket.id}`}/> : <p>This ticket cannot be used for admission.</p>}
      <p className="ticket-code">{ticket.id}</p>
      {ticket.checkedInAt && <p>Checked in: {formatEventTime(ticket.checkedInAt)}</p>}
    </article>)}</div>
  </main>;
}
function TicketQr({code}:{code:string}) {
  const [image,setImage]=useState("");
  const [error,setError]=useState(false);
  useEffect(()=>{
    let active=true;
    setImage(""); setError(false);
    import("qrcode").then(qr=>qr.toDataURL(code,{width:320,margin:4,errorCorrectionLevel:"M"}))
      .then(value=>{if(active)setImage(value);}).catch(()=>{if(active)setError(true);});
    return ()=>{active=false;};
  },[code]);
  return image ? <img src={image} alt="Ticket QR code" width={320} height={320} style={{maxWidth:"100%",height:"auto"}}/>
    : <p role="status">{error ? "Unable to display QR code. Staff can enter the ticket code below." : "Preparing QR code…"}</p>;
}
