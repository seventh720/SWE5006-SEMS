import { type FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../shared/api/client";
import { useAuth } from "../auth/AuthContext";
import { PersonalInfoFields } from "./PersonalInfoFields";
import { PrivacyNotice } from "./PrivacyNotice";
import { readProfile, saveProfile, type ProfileInfo } from "./profile";

export function ProfilePage() {
  const { token, logout } = useAuth();
  const [info, setInfo] = useState<ProfileInfo>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [message, setMessage] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(null); setLoadError(false);
    if (token) readProfile(token, controller.signal)
      .then((value) => { if (!controller.signal.aborted) setInfo(value); })
      .catch((caught: unknown) => { if (!controller.signal.aborted) { setError(caught); setLoadError(true); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [token, attempt]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!token || saving || loading || loadError) return;
    setSaving(true); setError(null); setMessage("");
    try {
      setInfo(await saveProfile(token, info));
      setMessage("Saved details updated. Existing bookings are unchanged.");
    } catch (caught) { setError(caught); }
    finally { setSaving(false); }
  }
  const expired = error instanceof ApiError && error.status === 401;
  const detail = error instanceof ApiError
    ? Object.values(error.problem.fieldErrors ?? {})[0] ?? error.message
    : "We couldn't load or save your details. Please try again.";
  return <main className="app-shell draft-shell">
    <Link className="text-link" to="/">← Dashboard</Link>
    <header className="organizer-header"><h1>Saved booking details</h1><p>Save frequently used details, then apply them when booking. All fields are optional.</p></header>
    <PrivacyNotice saved />
    {loading ? <p role="status">Loading saved details…</p> : <form className="card draft-form" onSubmit={submit}>
      <fieldset disabled={saving || loadError || expired}>
        <legend>Personal information</legend>
        <PersonalInfoFields value={info} onChange={(value) => { setInfo(value); setMessage(""); }} />
      </fieldset>
      <div className="button-row">
        <button type="submit" className="primary-button compact" disabled={saving || loadError || expired}>{saving ? "Saving…" : "Save details"}</button>
        <button type="button" className="secondary-button" disabled={saving || loadError || expired} onClick={() => { setInfo({}); setMessage("Fields cleared. Select Save details to remove them from your account."); }}>Clear fields</button>
      </div>
    </form>}
    {message && <p role="status" className="result-message">{message}</p>}
    {error != null && <div role="alert"><p className="error-message">{expired ? "Your session has expired. Please sign in again." : detail}</p>
      {expired ? <button className="secondary-button" onClick={logout}>Sign in again</button>
        : loadError && <button className="secondary-button" onClick={() => setAttempt((value) => value + 1)}>Try again</button>}
    </div>}
  </main>;
}
