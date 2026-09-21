import { useEffect, useState } from "react";
import { apiRequest, ApiError } from "../../shared/api/client";

type Result<T> = { path: string; data?: T; error?: string };

export function useEventRequest<T>(path: string, detail = false) {
  const [result, setResult] = useState<Result<T> | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    apiRequest<T>(path, { signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) setResult({ path, data });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        const message = detail && error instanceof ApiError && error.status === 404
          ? "This event is unavailable. It may no longer be published."
          : "We couldn't load events right now. Please try again later.";
        setResult({ path, error: message });
      });
    return () => controller.abort();
  }, [path, detail, attempt]);

  const current = result?.path === path ? result : null;
  return {
    data: current?.data,
    error: current?.error,
    loading: !current,
    retry: () => { setResult(null); setAttempt((value) => value + 1); },
  };
}
