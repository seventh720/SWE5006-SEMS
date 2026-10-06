import { type AttendeeInfo } from "./attendeeInfo";

export function AttendeeInfoSummary({ info, customFieldLabel }: { info?: AttendeeInfo; customFieldLabel?: string | null }) {
  if (!info || !Object.values(info).some(Boolean)) return null;
  return <section aria-label="Booking information">
    <h3>Booking information</h3>
    <dl className="event-facts">
      {info.realName && <><dt>Real name</dt><dd>{info.realName}</dd></>}
      {info.email && <><dt>Email</dt><dd>{info.email}</dd></>}
      {info.phone && <><dt>Phone</dt><dd>{info.phone}</dd></>}
      {info.studentId && <><dt>Student ID number</dt><dd>{info.studentId}</dd></>}
      {info.passportNumber && <><dt>Passport number</dt><dd>{info.passportNumber}</dd></>}
      {info.customAnswer && <><dt>{customFieldLabel ?? "Additional information"}</dt><dd>{info.customAnswer}</dd></>}
    </dl>
  </section>;
}
