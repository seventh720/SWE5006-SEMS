export type SessionSignal = { token: string; reason: "expired" | "roles" | "forbidden" };
const eventName = "sems-session-change";

export function signalSessionChange(signal: SessionSignal) {
  window.dispatchEvent(new CustomEvent<SessionSignal>(eventName, { detail: signal }));
}

export function onSessionChange(listener: (signal: SessionSignal) => void) {
  const handler = (event: Event) => listener((event as CustomEvent<SessionSignal>).detail);
  window.addEventListener(eventName, handler);
  return () => window.removeEventListener(eventName, handler);
}
