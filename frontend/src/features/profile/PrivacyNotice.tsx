export function PrivacyNotice({ saved = false, preRegistration = false, compact = false }: { saved?: boolean; preRegistration?: boolean; compact?: boolean }) {
  const notice = <p className="privacy-notice">We protect your privacy by restricting access to your information. {preRegistration
    ? "Your pre-filled details are private to your account until you submit a booking. Only then are the submitted details shared with the event organizer."
    : saved
    ? "Your saved details are private to your account. Only the details you choose to submit with a booking are shared with that event's organizer. You can update or clear saved details here."
    : "The details submitted with this order are visible to you and this event's organizer for event registration. Other attendees cannot view them. Changes here apply only to this booking."}</p>;
  return compact ? <details className="privacy-summary"><summary>{preRegistration
    ? "Your pre-filled details stay private until you book."
    : "Your details are shared only with this event’s organizer."} <span>Privacy details</span></summary>{notice}</details> : notice;
}
