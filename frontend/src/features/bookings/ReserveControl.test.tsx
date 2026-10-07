// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ReserveControl } from "./ReserveControl";

const auth = vi.hoisted(() => ({ token: "test-token", user: { roles: ["ATTENDEE"] }, logout: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => auth }));

const fetchMock = vi.fn<typeof fetch>();
function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
function idempotencyKey(options?: RequestInit): string {
  const headers = options?.headers as Record<string, string> | undefined;
  return String(headers?.["Idempotency-Key"] ?? "");
}
function mount(onBooked = vi.fn()) {
  return render(<MemoryRouter initialEntries={["/events/e1"]}><Routes>
    <Route path="/events/:id" element={<ReserveControl eventId="e1" ticketTypeId="t1" remaining={7} onBooked={onBooked} />} />
    <Route path="/login" element={<p>Sign-in page</p>} />
  </Routes></MemoryRouter>);
}
beforeEach(() => {
  auth.token = "test-token";
  auth.user = { roles: ["ATTENDEE"] };
  auth.logout.mockClear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("attendee free-ticket reservation", () => {
  it("rejects an invalid quantity before sending a request", () => {
    mount();
    fireEvent.change(screen.getByLabelText("Quantity"), { target: { value: "0" } });
    expect(screen.getByText("Enter a positive whole number of tickets.")).toBeTruthy();
    expect((screen.getByRole("button", { name: "Reserve" }) as HTMLButtonElement).disabled).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects a quantity above remaining availability before sending", () => {
    mount();
    fireEvent.change(screen.getByLabelText("Quantity"), { target: { value: "8" } });
    expect(screen.getByText("Only 7 of these tickets remain.")).toBeTruthy();
    expect((screen.getByRole("button", { name: "Reserve" }) as HTMLButtonElement).disabled).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("reserves with the attendee token, idempotency key and a minimal body", async () => {
    fetchMock.mockResolvedValue(response({ id: "booking-1" }, 201));
    mount();
    fireEvent.change(screen.getByLabelText("Quantity"), { target: { value: "2" } });
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("Reservation confirmed.");
    const post = fetchMock.mock.calls.find(([, options]) => options?.method === "POST")!;
    expect(post[0]).toBe("/api/v1/bookings");
    expect(post[1]?.headers).toMatchObject({
      Authorization: "Bearer test-token",
      "Idempotency-Key": expect.any(String),
    });
    expect(JSON.parse(post[1]?.body as string)).toEqual({ eventId: "e1", ticketTypeId: "t1", quantity: 2 });
  });

  it("does not send identity, price, total or status in the request body", async () => {
    fetchMock.mockResolvedValue(response({ id: "booking-1" }, 201));
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("Reservation confirmed.");
    const post = fetchMock.mock.calls.find(([, options]) => options?.method === "POST")!;
    const body = JSON.parse(post[1]?.body as string);
    for (const key of ["userId", "organizerId", "price", "priceMinor", "total", "totalAmount", "amount", "status"]) {
      expect(body).not.toHaveProperty(key);
    }
  });

  it("prevents duplicate submissions while a reservation is pending", async () => {
    let resolvePost!: (value: Response) => void;
    fetchMock.mockImplementation(async (_url, options) => {
      if (options?.method === "POST") return new Promise((resolve) => { resolvePost = resolve; });
      return response([]);
    });
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    expect((screen.getByRole("button", { name: "Reserving…" }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Reserving…" }));
    resolvePost(response({ id: "booking-1" }, 201));
    await screen.findByText("Reservation confirmed.");
    expect(fetchMock.mock.calls.filter(([, options]) => options?.method === "POST")).toHaveLength(1);
  });

  it("shows a confirmation with the booking id and refreshes availability", async () => {
    fetchMock.mockResolvedValue(response({ id: "booking-1" }, 201));
    const onBooked = vi.fn();
    mount(onBooked);
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("Reservation confirmed.");
    expect(screen.getByText("booking-1")).toBeTruthy();
    expect(screen.getByText(/Electronic tickets are not issued yet/)).toBeTruthy();
    expect(onBooked).toHaveBeenCalledOnce();
  });

  it("links to My Orders after a successful reservation", async () => {
    fetchMock.mockResolvedValue(response({ id: "booking-1" }, 201));
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("Reservation confirmed.");
    expect(screen.getByRole("link", { name: "View my orders" }).getAttribute("href")).toBe("/bookings");
  });

  it("allows a new reservation attempt after a successful one", async () => {
    fetchMock.mockResolvedValueOnce(response({ id: "booking-1" }, 201)).mockResolvedValueOnce(response({ id: "booking-2" }, 201));
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("Reservation confirmed.");
    expect(screen.getByText("booking-1")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Reserve more" }));
    expect(screen.queryByText("Reservation confirmed.")).toBeNull();
    expect((screen.getByLabelText("Quantity") as HTMLInputElement).value).toBe("1");
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("Reservation confirmed.");
    expect(screen.getByText("booking-2")).toBeTruthy();
  });

  it("uses a different Idempotency-Key for a new reservation after success", async () => {
    const keys: string[] = [];
    fetchMock.mockImplementation(async (_url, options) => {
      if (options?.method === "POST") {
        keys.push(idempotencyKey(options));
        return response({ id: `booking-${keys.length}` }, 201);
      }
      return response([]);
    });
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("Reservation confirmed.");
    fireEvent.click(screen.getByRole("button", { name: "Reserve more" }));
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("booking-2");
    expect(keys).toHaveLength(2);
    expect(keys[0]).not.toBe(keys[1]);
  });

  it("does not submit for a user without the attendee role", () => {
    auth.user = { roles: ["ORGANIZER"] };
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    expect(screen.getByText("You need an attendee role to book tickets.")).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("allows a multi-role user that includes attendee to book", async () => {
    auth.user = { roles: ["ORGANIZER", "ATTENDEE"] };
    fetchMock.mockResolvedValue(response({ id: "booking-1" }, 201));
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("Reservation confirmed.");
    expect(fetchMock.mock.calls.some(([, options]) => options?.method === "POST")).toBe(true);
  });

  it("offers sign-in-again when the token expires", async () => {
    fetchMock.mockResolvedValue(response({ detail: "Expired" }, 401));
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    expect(await screen.findByText("Your session has expired. Please sign in again.")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Sign in again" }));
    expect(auth.logout).toHaveBeenCalledOnce();
    expect(screen.getByText("Sign-in page")).toBeTruthy();
  });

  it("surfaces the backend conflict detail for a 409", async () => {
    fetchMock.mockResolvedValue(response({ detail: "This ticket type is already sold out" }, 409));
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    expect(await screen.findByText("This ticket type is already sold out")).toBeTruthy();
  });

  it("offers a retry when the network fails", async () => {
    fetchMock.mockRejectedValueOnce(new Error("network")).mockResolvedValueOnce(response({ id: "booking-1" }, 201));
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    expect(await screen.findByText(/couldn't confirm your reservation/i)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Retry reservation" }));
    await screen.findByText("Reservation confirmed.");
  });

  it("reuses the same Idempotency-Key when retrying an uncertain failure", async () => {
    const keys: string[] = [];
    fetchMock.mockImplementation(async (_url, options) => {
      if (options?.method === "POST") {
        keys.push(idempotencyKey(options));
        if (keys.length === 1) throw new Error("network");
        return response({ id: "booking-1" }, 201);
      }
      return response([]);
    });
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText(/couldn't confirm your reservation/i);
    fireEvent.click(screen.getByRole("button", { name: "Retry reservation" }));
    await screen.findByText("Reservation confirmed.");
    expect(keys).toHaveLength(2);
    expect(keys[0]).not.toBe("");
    expect(keys[0]).toBe(keys[1]);
  });

  it("uses a new Idempotency-Key for a new reservation attempt", async () => {
    const keys: string[] = [];
    fetchMock.mockImplementation(async (_url, options) => {
      if (options?.method === "POST") {
        keys.push(idempotencyKey(options));
        return response({ detail: "unavailable" }, 409);
      }
      return response([]);
    });
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("unavailable");
    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
    await screen.findByText("unavailable");
    expect(keys).toHaveLength(2);
    expect(keys[0]).not.toBe(keys[1]);
  });
});

function mountInformation(requirements = { realName: true, email: true, phone: true, studentId: true, passport: true, customFieldLabel: "Department" }) {
  return render(<MemoryRouter><ReserveControl eventId="e1" ticketTypeId="t1" remaining={7}
    bookingRequirements={requirements} onBooked={vi.fn()} /></MemoryRouter>);
}
function fillInformation() {
  for (const [label, value] of [["Real name", "Alice Tan"], ["Contact email", "contact@example.test"],
    ["Phone", "+65 8123 4567"], ["Student ID number", "A1234567"], ["Passport number", "P1234567"], ["Department", "Computing"]]) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  }
}

it("requires the organizer's selected fields and submits their values once per order", async () => {
  fetchMock.mockResolvedValue(response({ id: "booking-info" }, 201));
  mountInformation();
  fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
  expect(fetchMock).not.toHaveBeenCalled();
  fillInformation();
  fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
  await screen.findByText("Reservation confirmed.");
  expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string)).toMatchObject({
    attendeeInfo: { realName: "Alice Tan", email: "contact@example.test", phone: "+65 8123 4567",
      studentId: "A1234567", passportNumber: "P1234567", customAnswer: "Computing" },
  });
});

it("hides information the organizer did not request", async () => {
  fetchMock.mockResolvedValue(response({ id: "booking-info" }, 201));
  mountInformation({ realName: true, email: false, phone: false, studentId: false, passport: false, customFieldLabel: "" });
  expect(screen.queryByLabelText("Contact email")).toBeNull();
  expect(screen.queryByLabelText("Phone")).toBeNull();
  expect(screen.queryByLabelText("Passport number")).toBeNull();
  fireEvent.change(screen.getByLabelText("Real name"), { target: { value: "Alice Tan" } });
  fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
  await screen.findByText("Reservation confirmed.");
  expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string).attendeeInfo).toEqual({ realName: "Alice Tan" });
});

it("locks the contact details after an uncertain response and retries exactly the same order", async () => {
  fetchMock.mockRejectedValueOnce(new Error("network")).mockResolvedValueOnce(response({ id: "booking-info" }, 201));
  mountInformation();
  fillInformation();
  fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
  await screen.findByText(/couldn't confirm your reservation/i);
  expect(screen.getByLabelText("Real name").closest("fieldset")?.disabled).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Retry reservation" }));
  await screen.findByText("Reservation confirmed.");
  expect(fetchMock.mock.calls[0][1]?.body).toBe(fetchMock.mock.calls[1][1]?.body);
  expect(idempotencyKey(fetchMock.mock.calls[0][1])).toBe(idempotencyKey(fetchMock.mock.calls[1][1]));
});

it("applies only requested saved fields, permits overrides, and leaves the profile unchanged", async () => {
  fetchMock.mockImplementation(async (url) => String(url) === "/api/v1/profile"
    ? response({ realName: "Saved Name", email: "saved@example.test", studentId: "PRIVATE-ID", passportNumber: "PRIVATE-PASSPORT" })
    : response({ id: "booking-override" }, 201));
  mountInformation({ realName: true, email: true, phone: false, studentId: false, passport: false, customFieldLabel: "" });
  expect(fetchMock).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Apply saved details" }));
  await screen.findByText("Saved details applied. You can edit them for this booking.");
  expect((screen.getByLabelText("Real name") as HTMLInputElement).value).toBe("Saved Name");
  fireEvent.change(screen.getByLabelText("Real name"), { target: { value: "Guest Name" } });
  fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
  await screen.findByText("Reservation confirmed.");
  const post = fetchMock.mock.calls.find(([, options]) => options?.method === "POST")!;
  expect(JSON.parse(post[1]?.body as string).attendeeInfo).toEqual({ realName: "Guest Name", email: "saved@example.test" });
  expect(fetchMock.mock.calls.some(([, options]) => options?.method === "PUT")).toBe(false);
});

it("lets attendees enter information if saved details cannot be loaded", async () => {
  fetchMock.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(response({ id: "booking-manual" }, 201));
  mountInformation({ realName: true, email: false, phone: false, studentId: false, passport: false, customFieldLabel: "" });
  fireEvent.click(screen.getByRole("button", { name: "Apply saved details" }));
  await screen.findByText(/couldn't load saved details/);
  fireEvent.change(screen.getByLabelText("Real name"), { target: { value: "Manual Name" } });
  fireEvent.click(screen.getByRole("button", { name: "Reserve" }));
  await screen.findByText("Reservation confirmed.");
});

it("shows student ID and passport independently and displays the privacy notice", () => {
  mountInformation({ realName: false, email: false, phone: false, studentId: true, passport: false, customFieldLabel: "Department" });
  expect(screen.getByLabelText("Student ID number")).toBeTruthy();
  expect(screen.queryByLabelText("Passport number")).toBeNull();
  expect(screen.getByLabelText("Department")).toBeTruthy();
  expect(screen.getByText(/We protect your privacy/)).toBeTruthy();
});
