import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiRequest, ApiError } from "../../shared/api/client";
import { hasRole, roleLabel } from "../../shared/auth/roles";

export function DashboardPage() {
  const { user, token, logout } = useAuth();
  const [demoResult, setDemoResult] = useState("");
  if (!user || !token) return null;

  async function checkAccess(path: string) {
    try {
      const response = await apiRequest<{ message: string }>(path, {}, token!);
      setDemoResult(response.message);
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) {
        setDemoResult("Access denied: the current role is not authorized.");
      } else {
        setDemoResult(error instanceof Error ? error.message : "Request failed");
      }
    }
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">SEMS · Your event hub</p>
          <h1>My dashboard</h1>
        </div>
        <button className="secondary-button" onClick={logout}>Logout</button>
      </header>

      <section className="card event-dashboard-entry">
        <div><p className="section-label">Discover events</p><h2>Find your next experience</h2><p>Browse published events and explore the details.</p></div>
        <Link className="text-link" to="/events">Explore events →</Link>
        {(hasRole(user.roles, "ORGANIZER") || hasRole(user.roles, "ADMIN")) &&
          <Link className="text-link" to="/organizer/events">My events →</Link>}
      </section>

      <section className="profile-grid">
        <article className="card profile-card">
          <p className="section-label">Signed-in account</p>
          <h2>{user.username}</h2>
          <p>{user.email}</p>
          <p className="status">{user.status}</p>
        </article>

        <article className="card">
          <p className="section-label">Assigned roles</p>
          <div className="role-list">
            {user.roles.map((role) => <span className="role-chip" key={role}>{roleLabel(role)}</span>)}
          </div>
          {hasRole(user.roles, "ADMIN") && (
            <Link className="text-link" to="/admin/users">Manage users and roles</Link>
          )}
        </article>
      </section>

      <section className="card demo-card">
        <div>
          <p className="section-label">RBAC acceptance check</p>
          <h2>Test protected endpoints</h2>
          <p>These requests are authorized by the backend. Hiding a frontend button is not treated as security.</p>
        </div>
        <div className="button-row">
          <button className="secondary-button" onClick={() => checkAccess("/api/v1/demo/organizer")}>
            Check organizer access
          </button>
          <button className="secondary-button" onClick={() => checkAccess("/api/v1/demo/staff")}>
            Check staff access
          </button>
        </div>
        {demoResult && <p className="result-message">{demoResult}</p>}
      </section>
    </main>
  );
}
