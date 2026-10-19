import { signalSessionChange } from "../../shared/auth/sessionEvents";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, Navigate, useSearchParams, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiRequest, ApiError } from "../../shared/api/client";
import { hasRole, roleLabel } from "../../shared/auth/roles";
import { roles, type Role, type User } from "../../shared/types/auth";

export function AdminUsersPage() {
  const { user: currentUser, token, logout } = useAuth();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const rawPage = Number(params.get("page") ?? "0");
  const page = Number.isSafeInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const keyword = params.get("keyword") ?? "";
  const [search, setSearch] = useState(keyword);
  const [meta, setMeta] = useState<{ totalElements: number; totalPages: number } | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [expired, setExpired] = useState(false);
  const path = `/api/v1/admin/users?${new URLSearchParams({ page: String(page), size: "10", keyword })}`;
  const currentRequest = useRef("");
  currentRequest.current = `${token}:${path}`;
  useEffect(() => setSearch(keyword), [keyword]);
  const [users, setUsers] = useState<User[]>([]);
  const [error, setError] = useState("");
  const [savingId, setSavingId] = useState<string | null>(null);

  useEffect(() => {
    if (!token || !currentUser || !hasRole(currentUser.roles, "ADMIN")) return;
    const controller = new AbortController();
    setUsers([]); setMeta(null); setError(""); setExpired(false);
    apiRequest<{ items: User[]; totalElements: number; totalPages: number }>(path, { signal: controller.signal }, token)
      .then(data => { if (!controller.signal.aborted) { setUsers(data.items); setMeta(data); } })
      .catch(caught => { if (!controller.signal.aborted) {
        setError(caught instanceof ApiError ? caught.message : "Unable to load users");
        setExpired(caught instanceof ApiError && caught.status === 401);
      } });
    return () => controller.abort();
  }, [currentUser, token, path, attempt]);

  function move(next: number, query = keyword) { setParams({ page: String(next), ...(query ? { keyword: query } : {}) }); }
  function submit(event: FormEvent) { event.preventDefault(); move(0, search.trim()); }

  if (!currentUser || !token) return null;
  if (!hasRole(currentUser.roles, "ADMIN")) return <Navigate to="/" replace />;

  function toggleRole(userId: string, role: Role) {
    setUsers((current) => current.map((item) => {
      if (item.id !== userId) return item;
      const nextRoles = item.roles.includes(role)
        ? item.roles.filter((candidate) => candidate !== role)
        : [...item.roles, role];
      return nextRoles.length === 0 ? item : { ...item, roles: nextRoles };
    }));
  }

  async function saveRoles(target: User) {
    const request = currentRequest.current;
    setSavingId(target.id);
    setError("");
    try {
      const updated = await apiRequest<User>(
        `/api/v1/admin/users/${target.id}/roles`,
        { method: "PUT", body: JSON.stringify({ roles: target.roles }) },
        token!,
      );
      if (updated.id === currentUser?.id && [...updated.roles].sort().join(",") !== [...currentUser.roles].sort().join(",")) {
        signalSessionChange({ token: token!, reason: "roles" });
      }
      if (request === currentRequest.current) setUsers((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (caught) {
      if (request === currentRequest.current) { setError(caught instanceof ApiError ? caught.message : "Unable to update roles"); setExpired(caught instanceof ApiError && caught.status === 401); }
    } finally {
      setSavingId(null);
    }
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div>
          <Link className="text-link" to="/">Back to dashboard</Link>
          <h1>User and role management</h1>
        </div>
      </header>

      <form className="card filter-form" role="search" onSubmit={submit}>
        <label>Search username or email<input type="search" value={search} onChange={e => setSearch(e.target.value)} /></label>
        <button className="primary-button compact" disabled={savingId != null}>Search users</button>
        {keyword && <button type="button" className="secondary-button" disabled={savingId != null} onClick={() => move(0, "")}>Clear search</button>}
      </form>
      {error && <p role="alert" className="error-message">{error}</p>}
      {expired ? <button className="secondary-button" onClick={() => { logout(); navigate("/login"); }}>Sign in again</button>
        : error && !meta ? <button className="secondary-button" onClick={() => setAttempt(n => n + 1)}>Try again</button> : null}
      {!meta && !error && <p role="status">Loading users…</p>}
      {meta && <p role="status">{meta.totalElements} accounts found</p>}
      {meta && users.length === 0 && <p className="card">No matching accounts on this page.</p>}
      <section className="user-list">
        {users.map((item) => (
          <article className="card user-card" key={item.id}>
            <div>
              <h2>{item.username}</h2>
              <p>{item.email}</p>
              <p className="muted">{item.status}</p>
            </div>
            <fieldset disabled={savingId != null}>
              <legend>Roles</legend>
              {roles.map((role) => (
                <label className="role-option" key={role}>
                  <input
                    type="checkbox"
                    checked={item.roles.includes(role)}
                    onChange={() => toggleRole(item.id, role)}
                  />
                  {roleLabel(role)}
                </label>
              ))}
            </fieldset>
            <button
              className="primary-button compact"
              disabled={savingId != null}
              onClick={() => saveRoles(item)}
            >
              {savingId === item.id ? "Saving..." : "Save roles"}
            </button>
          </article>
        ))}
      </section>
      {meta && <nav className="event-pagination" aria-label="User pages">
        <button className="secondary-button" disabled={page === 0 || savingId != null} onClick={() => move(Math.max(0, page - 1))}>Previous</button>
        <span>Page {page + 1} of {Math.max(1, meta.totalPages)}</span>
        <button className="secondary-button" disabled={page + 1 >= meta.totalPages || savingId != null} onClick={() => move(page + 1)}>Next</button>
      </nav>}
    </main>
  );
}
