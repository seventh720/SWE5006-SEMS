import {
  createContext,
  type PropsWithChildren,
  useContext,
  useMemo,
  useState,
} from "react";
import { apiRequest } from "../../shared/api/client";
import type { AuthResponse, User } from "../../shared/types/auth";

interface AuthState {
  token: string;
  user: User;
}

interface AuthContextValue {
  token: string | null;
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  changeUsername: (username: string) => Promise<string>;
}

const STORAGE_KEY = "sems-auth";
const AuthContext = createContext<AuthContextValue | null>(null);

function loadStoredState(): AuthState | null {
  const value = sessionStorage.getItem(STORAGE_KEY);
  if (!value) return null;
  try {
    return JSON.parse(value) as AuthState;
  } catch {
    sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [state, setState] = useState<AuthState | null>(loadStoredState);

  async function login(email: string, password: string) {
    const response = await apiRequest<AuthResponse>("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    const next = { token: response.accessToken, user: response.user };
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
      token: state?.token ?? null,
      user: state?.user ?? null,
      login,
      register,
      logout,
      changeUsername,
    }),
    [state],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
