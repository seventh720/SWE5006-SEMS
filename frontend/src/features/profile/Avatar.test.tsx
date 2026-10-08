// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { Avatar, AvatarImage } from "./Avatar";
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ token: "avatar-token" }) }));
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
const response = (dataUrl: string | null) => new Response(JSON.stringify({ dataUrl }), { status: 200 });
it("shows a default avatar and falls back if an uploaded image cannot load", () => {
  const { rerender } = render(<AvatarImage />);
  expect(screen.getByRole("img", { name: "Default avatar" })).toBeTruthy();
  rerender(<AvatarImage src="data:image/png;base64,broken" />);
  fireEvent.error(screen.getByRole("img", { name: "Your avatar" }));
  expect(screen.getByRole("img", { name: "Default avatar" })).toBeTruthy();
});
it("loads a saved avatar and removes it through the authenticated API", async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(response("data:image/png;base64,abc")).mockResolvedValueOnce(response(null));
  vi.stubGlobal("fetch", fetchMock); render(<Avatar editable />);
  await screen.findByRole("img", { name: "Your avatar" });
  fireEvent.click(screen.getByRole("button", { name: "Use default avatar" }));
  await screen.findByText("Default avatar restored.");
  expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: "PUT", body: '{"dataUrl":null}', headers: { Authorization: "Bearer avatar-token" } });
  expect(screen.getByRole("img", { name: "Default avatar" })).toBeTruthy();
});
it("rejects unsupported files before uploading", async () => {
  const fetchMock = vi.fn().mockResolvedValue(response(null)); vi.stubGlobal("fetch", fetchMock);
  render(<Avatar editable />);
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  fireEvent.change(screen.getByLabelText("Upload avatar"), { target: { files: [new File(["svg"], "image.svg", { type: "image/svg+xml" })] } });
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it("quietly keeps the default avatar if loading fails", async () => {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
  render(<Avatar editable />);
  await waitFor(() => expect(screen.getByRole("button", { name: "Upload avatar" })).toBeTruthy());
  expect(screen.getByRole("img", { name: "Default avatar" })).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
  expect((screen.getByRole("button", { name: "Use default avatar" }) as HTMLButtonElement).disabled).toBe(true);
});
