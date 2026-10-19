import { useEffect, useState } from "react";

export function eventPhase(startsAt: string, endsAt: string, now = Date.now()) {
  return now >= Date.parse(endsAt) ? "Ended" : now >= Date.parse(startsAt) ? "Ongoing" : "Upcoming";
}

export function EventPhase({ startsAt, endsAt, status = "PUBLISHED" }: { startsAt: string; endsAt: string; status?: string }) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);
  const label = status === "PUBLISHED" ? eventPhase(startsAt, endsAt, now) : status;
  return <span className={`event-status status-${label.toLowerCase()}`}>{label}</span>;
}
