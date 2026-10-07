export function PrivacyNotice({ saved = false }: { saved?: boolean }) {
  return <p className="privacy-notice">We protect your privacy by restricting access to your information. {saved
    ? "Your saved details are private to your account. Only the details you choose to submit with a booking are shared with that event's organizer. You can update or clear saved details here."
    : "The details submitted with this order are visible to you and this event's organizer for event registration. Other attendees cannot view them. Changes here apply only to this booking."}</p>;
}
