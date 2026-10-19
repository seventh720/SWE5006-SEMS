import { useEffect, useState } from "react";
import { apiRequest } from "../../shared/api/client";
import { useAuth } from "../auth/AuthContext";

export function useManagementRequest<T>(path: string) {
  const { token } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ path: string; token: string; data?: T; error?: unknown } | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    if (token) apiRequest<T>(path, { signal: controller.signal }, token)
      .then((data) => { if (!controller.signal.aborted) setResult({ path, token, data }); })
      .catch((error: unknown) => { if (!controller.signal.aborted) setResult({ path, token, error }); });
    return () => controller.abort();
  }, [path, token, attempt]);
  const current = result?.path === path && result.token === token ? result : null;
  return { data: current?.data, error: current?.error, loading: !current,
    reload: () => { setResult(null); setAttempt((value) => value + 1); } };
}
