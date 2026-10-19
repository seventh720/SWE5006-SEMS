import {
  createContext,
  type PropsWithChildren,
  useContext,
  useEffect,
  useRef,
  useCallback,
  useMemo,
  useState,
} from "react";
import { apiRequest, ApiError } from "../../shared/api/client";
import type { AuthResponse, User } from "../../shared/types/auth";

import { onSessionChange } from "../../shared/auth/sessionEvents";

interface AuthState {
  expiresAt: number;
  token: string;
  user: User;
}

interface AuthContextValue {
  sessionNotice: string;
  token: string | null;
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  changeUsername: (username: string) => Promise<string>;
}

const STORAGE_KEY = "sems-auth";
const NOTICE_KEY = "sems-session-notice";
const expiredNotice = "Your session has expired. Please sign in again.";
const rolesNotice = "Your account roles have changed. Please sign in again to refresh your permissions. A notification has been saved in your inbox.";

function jwtExpiry(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.exp === "number" ? payload.exp * 1000 : null;
  } catch { return null; }
}

function sameRoles(left: User["roles"], right: User["roles"]) {
  return [...left].sort().join(",") === [...right].sort().join(",");
}
const AuthContext = createContext<AuthContextValue | null>(null);

function loadStoredState(): AuthState | null {
  const value = sessionStorage.getItem(STORAGE_KEY);
  if (!value) return null;
  try {
    const stored = JSON.parse(value) as AuthState;
    const expiresAt = jwtExpiry(stored.token) ?? stored.expiresAt;
    if (!stored.token || !stored.user?.id || !Array.isArray(stored.user.roles)
        || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY);
      sessionStorage.setItem(NOTICE_KEY, expiredNotice);
      return null;
    }
    return { ...stored, expiresAt };
  } catch {
    sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [state, setState] = useState<AuthState | null>(loadStoredState);
  const [sessionNotice, setSessionNotice] = useState(() => sessionStorage.getItem(NOTICE_KEY) ?? "");
  const current = useRef(state);
  current.current = state;
  const validatedToken = useRef<string | null>(null);
  const authAttempt = useRef(0);

  const invalidate = useCallback((token: string, reason: "expired" | "roles") => {
    if (current.current?.token !== token) return;
    const notice = reason === "roles" ? rolesNotice : expiredNotice;
    current.current = null;
    authAttempt.current++;
    sessionStorage.removeItem(STORAGE_KEY);
    sessionStorage.setItem(NOTICE_KEY, notice);
    setState(null);
    setSessionNotice(notice);
  }, []);

  const activeToken = state?.token;
  const expiresAt = state?.expiresAt;
  useEffect(() => {
    if (!activeToken || !expiresAt) return;
    let cancelled = false;
    let checking = false;
    async function refresh() {
      if (cancelled || checking || current.current?.token !== activeToken) return;
      if (Date.now() >= expiresAt!) { invalidate(activeToken!, "expired"); return; }
      checking = true;
      try {
        const user = await apiRequest<User>("/api/v1/auth/me", {}, activeToken);
        const session = current.current;
        if (cancelled || !session || session.token !== activeToken) return;
        if (user.status !== "ACTIVE") { invalidate(activeToken!, "expired"); return; }
        if (!sameRoles(user.roles, session.user.roles)) {
          invalidate(activeToken!, "roles");
        }
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) invalidate(activeToken!, "expired");
        // A temporary network failure does not discard a valid session.
      } finally { checking = false; }
    }
    if (validatedToken.current !== activeToken) void refresh();
    const timer = window.setTimeout(() => invalidate(activeToken, "expired"), Math.max(0, expiresAt - Date.now()));
    const poll = window.setInterval(() => { if (!document.hidden) void refresh(); }, 30_000);
    const focus = () => { if (!document.hidden) void refresh(); };
    window.addEventListener("focus", focus);
    document.addEventListener("visibilitychange", focus);
    const unsubscribe = onSessionChange(signal => {
      if (signal.token !== activeToken) return;
      if (signal.reason === "forbidden") void refresh();
      else invalidate(activeToken, signal.reason);
    });
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
      window.clearInterval(poll);
      window.removeEventListener("focus", focus);
      document.removeEventListener("visibilitychange", focus);
      unsubscribe();
    };
  }, [activeToken, expiresAt, invalidate]);

  async function login(email: string, password: string) {
    const attempt = ++authAttempt.current;
    const response = await apiRequest<AuthResponse>("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    if (attempt !== authAttempt.current) return;
    const expiresAt = Math.min(jwtExpiry(response.accessToken) ?? Infinity, Date.now() + response.expiresIn * 1000);
    const next = { token: response.accessToken, user: response.user, expiresAt };
    validatedToken.current = response.accessToken;
    sessionStorage.removeItem(NOTICE_KEY);
    setSessionNotice("");
    current.current = next;
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setState(next);
  }

  async function register(username: string, email: string, password: string) {
    await apiRequest<User>("/api/v1/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, email, password }),
    });
    await login(email, password);
  }

  function logout() {
    authAttempt.current++;
    current.current = null;
    sessionStorage.removeItem(NOTICE_KEY);
    setSessionNotice("");
    sessionStorage.removeItem(STORAGE_KEY);
    setState(null);
  }

  async function changeUsername(username: string) {
    if (!state) throw new Error("Please sign in again.");
    const originalToken = state.token;
    const result = await apiRequest<{ username: string }>("/api/v1/profile/username", {
      method: "PUT", body: JSON.stringify({ username }),
    }, originalToken);
    setState(current => {
      if (!current || current.token !== originalToken) return current;
      const next = { ...current, user: { ...current.user, username: result.username } };
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      return next;
    });
    return result.username;
  }

  const value = useMemo<AuthContextValue>(
    () => ({
      sessionNotice,
      token: state?.token ?? null,
      user: state?.user ?? null,
      login,
      register,
      logout,
      changeUsername,
    }),
    [state, sessionNotice],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
