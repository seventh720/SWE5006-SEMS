// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { DateTimeField } from "./DateTimeField";

afterEach(cleanup);

it("renders English calendar controls and applies a minute-only value", () => {
  document.documentElement.lang = "zh-CN";
  const onChange = vi.fn();
  const { container } = render(<DateTimeField name="starts" label="Start time" value="2026-10-29T19:11" onChange={onChange} />);
  fireEvent.click(screen.getByRole("button", { name: "Choose start time" }));
  fireEvent.click(screen.getByRole("combobox", { name: "Month" }));
  expect(screen.getByRole("option", { name: "October" })).toBeTruthy();
  fireEvent.click(screen.getByRole("option", { name: "October" }));
  expect(screen.getByText("Sun")).toBeTruthy();
  expect(container.querySelector('input[type="datetime-local"]')).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "October 30, 2026" }));
  fireEvent.click(screen.getByRole("combobox", { name: "Minute" }));
  fireEvent.click(screen.getByRole("option", { name: "15" }));
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  expect(onChange).toHaveBeenCalledWith("2026-10-30T19:15");
  expect(screen.queryByRole("region", { name: "Start time calendar" })).toBeNull();
  document.documentElement.lang = "en";
});

it("clamps the selected day across months and cancels without changing the field", () => {
  const onChange = vi.fn();
  render(<DateTimeField name="ends" label="End time" value="2028-01-31T09:00" onChange={onChange} />);
  fireEvent.click(screen.getByRole("button", { name: "Choose end time" }));
  fireEvent.click(screen.getByRole("button", { name: "Next month" }));
  expect(screen.getByRole("button", { name: "February 29, 2028" }).getAttribute("aria-pressed")).toBe("true");
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(onChange).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Choose end time" }));
  expect(screen.getByRole("button", { name: "January 31, 2028" }).getAttribute("aria-pressed")).toBe("true");
  fireEvent.keyDown(screen.getByLabelText("Month"), { key: "Escape" });
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "Choose end time" }));
});

it("opens on the start date and only offers later end times", () => {
  const onChange = vi.fn();
  render(<DateTimeField name="ends" label="End time" value="" after="2030-10-15T14:30" onChange={onChange} />);
  fireEvent.click(screen.getByRole("button", { name: "Choose end time" }));
  expect((screen.getByRole("button", { name: "October 14, 2030" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole("combobox", { name: "Hour" }));
  expect(screen.queryByRole("option", { name: "13" })).toBeNull();
  fireEvent.keyDown(screen.getByRole("combobox", { name: "Hour" }), { key: "Escape" });
  fireEvent.click(screen.getByRole("combobox", { name: "Minute" }));
  expect(screen.queryByRole("option", { name: "30" })).toBeNull();
  fireEvent.click(screen.getByRole("option", { name: "45" }));
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  expect(onChange).toHaveBeenCalledWith("2030-10-15T14:45");
});

it("moves the earliest end time into the next year after 23:59", () => {
  const onChange = vi.fn();
  render(<DateTimeField name="ends" label="End time" value="" after="2030-12-31T23:59" onChange={onChange} />);
  fireEvent.click(screen.getByRole("button", { name: "Choose end time" }));
  expect(screen.getByRole("button", { name: "January 1, 2031" }).getAttribute("aria-pressed")).toBe("true");
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  expect(onChange).toHaveBeenCalledWith("2031-01-01T00:00");
});

it("limits registration deadline to after opening and at or before event start", () => {
  const onChange = vi.fn();
  render(<DateTimeField name="deadline" label="Registration deadline" value="" after="2030-10-15T14:30" atOrBefore="2030-10-15T15:00" onChange={onChange} />);
  fireEvent.click(screen.getByRole("button", { name: "Choose registration deadline" }));
  expect((screen.getByRole("button", { name: "October 16, 2030" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole("combobox", { name: "Hour" }));
  expect(screen.queryByRole("option", { name: "16" })).toBeNull();
  fireEvent.click(screen.getByRole("option", { name: "15" }));
  fireEvent.click(screen.getByRole("combobox", { name: "Minute" }));
  expect(screen.getAllByRole("option")).toHaveLength(1);
  fireEvent.click(screen.getByRole("option", { name: "00" }));
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  expect(onChange).toHaveBeenCalledWith("2030-10-15T15:00");
});

it("positions registration opening before a midnight deadline in the previous month", () => {
  const onChange = vi.fn();
  render(<DateTimeField name="opening" label="Registration opens" value="" before="2030-11-01T00:00" onChange={onChange} />);
  fireEvent.click(screen.getByRole("button", { name: "Choose registration opens" }));
  expect(screen.getByRole("button", { name: "October 31, 2030" }).getAttribute("aria-pressed")).toBe("true");
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  expect(onChange).toHaveBeenCalledWith("2030-10-31T23:59");
});
