// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { Select } from "./Select";
afterEach(cleanup);
const options = [{ value: "one", label: "One" }, { value: "two", label: "Two" }, { value: "three", label: "Three" }];
it("supports keyboard selection and escape without committing", () => {
  const onChange = vi.fn();
  render(<Select label="Example" value="one" options={options} onChange={onChange} />);
  const trigger = screen.getByRole("combobox");
  fireEvent.keyDown(trigger, { key: "ArrowDown" });
  fireEvent.keyDown(trigger, { key: "Enter" });
  expect(onChange).toHaveBeenCalledWith("two");
  fireEvent.click(trigger); fireEvent.keyDown(trigger, { key: "End" }); fireEvent.keyDown(trigger, { key: "Escape" });
  expect(onChange).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole("listbox")).toBeNull();
});
it("supports typeahead, pointer selection and outside dismissal", () => {
  const onChange = vi.fn(); render(<Select label="Example" value="one" options={options} onChange={onChange} />);
  const trigger = screen.getByRole("combobox");
  fireEvent.keyDown(trigger, { key: "t" }); fireEvent.keyDown(trigger, { key: "h" }); fireEvent.keyDown(trigger, { key: "Enter" });
  expect(onChange).toHaveBeenCalledWith("three");
  fireEvent.click(trigger); fireEvent.click(screen.getByRole("option", { name: "Two" }));
  expect(onChange).toHaveBeenLastCalledWith("two");
  fireEvent.click(trigger); fireEvent.pointerDown(document.body);
  expect(screen.queryByRole("listbox")).toBeNull();
});
it("keeps disabled controls closed", () => {
  render(<Select label="Example" value="one" options={options} onChange={vi.fn()} disabled />);
  fireEvent.click(screen.getByRole("combobox"));
  expect(screen.queryByRole("listbox")).toBeNull();
});
