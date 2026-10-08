import { useEffect, useRef, useState } from "react";
import { apiRequest } from "../../shared/api/client";
import { useAuth } from "../auth/AuthContext";

type AvatarData = { dataUrl: string | null };

export function AvatarImage({ src }: { src?: string | null }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  return src && !failed ? <img className="avatar" src={src} alt="Your avatar" onError={() => setFailed(true)} />
    : <svg className="avatar default-avatar" viewBox="0 0 80 80" role="img" aria-label="Default avatar">
      <circle cx="40" cy="40" r="40" fill="#d7eeeb" /><circle cx="40" cy="29" r="14" fill="#42858a" />
      <path d="M14 72c0-30 52-30 52 0" fill="#42858a" />
    </svg>;
}

export function Avatar({ editable = false }: { editable?: boolean }) {
  const { token } = useAuth();
  const fileInput = useRef<HTMLInputElement>(null);
  const [data, setData] = useState<AvatarData>({ dataUrl: null });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setData({ dataUrl: null });
    if (token) apiRequest<AvatarData>("/api/v1/profile/avatar", { signal: controller.signal }, token)
      .then((result) => { if (!controller.signal.aborted) setData(result); })
      .catch(() => { /* Keep the default avatar when the saved image is unavailable. */ });
    return () => controller.abort();
  }, [token, editable]);

  async function save(dataUrl: string | null) {
    if (!token) return;
    setData(await apiRequest<AvatarData>("/api/v1/profile/avatar", { method: "PUT", body: JSON.stringify({ dataUrl }) }, token));
    setMessage(dataUrl ? "Avatar updated." : "Default avatar restored.");
  }
  async function upload(file?: File) {
    if (!file || busy) return;
    setError(""); setMessage("");
    if (!["image/png", "image/jpeg"].includes(file.type) || file.size > 5 * 1024 * 1024) {
      setError("Choose a PNG or JPEG image up to 5 MB."); return;
    }
    setBusy(true);
    const url = URL.createObjectURL(file);
    try {
      const image = new Image(); image.src = url; await image.decode();
      const canvas = document.createElement("canvas"); canvas.width = 256; canvas.height = 256;
      const context = canvas.getContext("2d"); if (!context) throw new Error("Canvas unavailable");
      context.fillStyle = "#ffffff"; context.fillRect(0, 0, 256, 256);
      const size = Math.min(image.naturalWidth, image.naturalHeight);
      context.drawImage(image, (image.naturalWidth - size) / 2, (image.naturalHeight - size) / 2, size, size, 0, 0, 256, 256);
      await save(canvas.toDataURL("image/jpeg", 0.88));
    } catch { setError("Couldn't save this image. Please try again with a PNG or JPEG."); }
    finally { URL.revokeObjectURL(url); setBusy(false); }
  }
  if (!editable) return <AvatarImage src={data.dataUrl} />;
  return <section className="card avatar-editor" aria-label="Profile picture">
    <AvatarImage src={data.dataUrl} /><div><h2>Profile picture</h2>
      <p className="muted">PNG or JPEG, up to 5 MB. Your image is cropped to a square and saved to your account.</p>
      <input ref={fileInput} hidden type="file" accept="image/png,image/jpeg" aria-label="Upload avatar" disabled={busy}
        onChange={(event) => { void upload(event.target.files?.[0]); event.target.value = ""; }} />
      <div className="button-row avatar-actions">
        <button type="button" className="secondary-button" disabled={busy} onClick={() => fileInput.current?.click()}>{busy ? "Saving…" : "Upload avatar"}</button>
        <button type="button" className="secondary-button" disabled={busy || !data.dataUrl} onClick={async () => {
          setBusy(true); setError(""); setMessage("");
          try { await save(null); } catch { setError("Couldn't remove your avatar. Please try again."); } finally { setBusy(false); }
        }}>Use default avatar</button></div>
      {error && <p role="alert" className="error-message">{error}</p>}{message && <p role="status" className="result-message">{message}</p>}
    </div>
  </section>;
}
