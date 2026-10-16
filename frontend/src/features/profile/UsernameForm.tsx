import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "../../shared/api/client";
import { useAuth } from "../auth/AuthContext";

export function UsernameForm() {
  const { user, changeUsername, logout } = useAuth();
  const [username, setUsername] = useState(user?.username ?? "");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState<unknown>(null);
  useEffect(() => { setUsername(user?.username ?? ""); }, [user?.username]);
  const expired = error instanceof ApiError && error.status === 401;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (saving || !user) return;
    setError(null); setMessage("");
    if (!/^[A-Za-z0-9._-]{3,50}$/.test(username)) {
      setError(new Error("Use 3–50 letters, numbers, dots, underscores or hyphens."));
      return;
    }
    setSaving(true);
    try {
      setUsername(await changeUsername(username));
      setMessage("Username updated.");
    } catch (caught) { setError(caught); }
    finally { setSaving(false); }
  }

  return <form className="card username-form" onSubmit={submit}>
    <h2>Username</h2>
    <label htmlFor="profile-username">Username
      <input id="profile-username" autoComplete="username" value={username} required minLength={3} maxLength={50}
        pattern="[A-Za-z0-9._\-]+" aria-describedby="username-help" disabled={saving || expired}
        onChange={event => { setUsername(event.target.value); setError(null); setMessage(""); }} />
    </label>
    <p id="username-help" className="muted">3–50 letters, numbers, dots, underscores or hyphens. Saved in lowercase. Your login email stays the same.</p>
    <button className="primary-button compact" type="submit" disabled={saving || expired || !user || username === user.username}>{saving ? "Saving…" : "Save username"}</button>
    {message && <p role="status" className="result-message">{message}</p>}
    {error != null && <div role="alert"><p className="error-message">{expired ? "Your session has expired. Please sign in again."
      : error instanceof ApiError ? error.problem.fieldErrors?.username ?? error.message
      : error instanceof Error ? error.message : "We couldn't save your username. Please try again."}</p>
      {expired && <button type="button" className="secondary-button" onClick={logout}>Sign in again</button>}
    </div>}
  </form>;
}
