import { type FormEvent, useState } from "react";
import { Navigate } from "react-router-dom";
import { ApiError } from "../../shared/api/client";
import { useAuth } from "./AuthContext";

export function AuthPage() {
  const { user, login, register } = useAuth();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (user) return <Navigate to="/" replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      if (mode === "register") {
        await register(username, email, password);
      } else {
        await login(email, password);
      }
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Unable to complete the request");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="brand-panel">
        <p className="eyebrow">SWE5006 Team 10</p>
        <h1>Smart Event Management and Ticketing System</h1>
        <p>Iteration 1 authentication and role-based access control.</p>
        <ul>
          <li>Secure password hashing</li>
          <li>Short-lived JWT authentication</li>
          <li>Four explicit system roles</li>
        </ul>
      </section>

      <section className="form-panel">
        <div className="auth-tabs" role="tablist" aria-label="Authentication mode">
          <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>
            Login
          </button>
          <button className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>
            Register
          </button>
        </div>

        <form onSubmit={submit}>
          <h2>{mode === "login" ? "Welcome back" : "Create attendee account"}</h2>
          {mode === "register" && (
            <label>
              Username
              <input
                autoComplete="username"
                minLength={3}
                maxLength={50}
                required
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </label>
          )}
          <label>
            Email
            <input
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label>
            Password
            <input
              type="password"
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              minLength={8}
              maxLength={72}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {error && <p className="error-message">{error}</p>}
          <button className="primary-button" disabled={submitting} type="submit">
            {submitting ? "Please wait..." : mode === "login" ? "Login" : "Register and login"}
          </button>
        </form>
      </section>
    </main>
  );
}
