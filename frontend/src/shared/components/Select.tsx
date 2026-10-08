import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";

type Option = { value: string; label: string };

export function Select({ label, value, options, onChange, autoFocus = false, disabled = false }: {
  label: string; value: string; options: readonly Option[]; onChange: (value: string) => void;
  autoFocus?: boolean; disabled?: boolean;
}) {
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const search = useRef({ text: "", time: 0 });
  const [open, setOpen] = useState(false);
  const selected = Math.max(0, options.findIndex((option) => option.value === value));
  const [active, setActive] = useState(selected);
  const [position, setPosition] = useState({ left: 0, top: 0, width: 0, maxHeight: 240 });
  useLayoutEffect(() => {
    if (!open) return;
    function place() {
      const rect = trigger.current?.getBoundingClientRect();
      if (!rect) return;
      const below = window.innerHeight - rect.bottom - 12;
      const above = rect.top - 12;
      const height = Math.min(240, Math.max(80, Math.max(below, above)));
      const up = below < Math.min(240, options.length * 40 + 12) && above > below;
      const width = Math.min(Math.max(rect.width, 100), window.innerWidth - 24);
      setPosition({ left: Math.max(12, Math.min(rect.left, window.innerWidth - width - 12)),
        top: up ? Math.max(12, rect.top - height - 6) : rect.bottom + 6, width, maxHeight: height });
    }
    place();
    function scroll(event: Event) { if (!menu.current?.contains(event.target as Node)) place(); }
    window.addEventListener("resize", place);
    document.addEventListener("scroll", scroll, true);
    return () => { window.removeEventListener("resize", place); document.removeEventListener("scroll", scroll, true); };
  }, [open, options.length]);
  useEffect(() => {
    const list = menu.current;
    const item = list?.querySelector<HTMLElement>(`[data-index="${active}"]`);
    if (!open || !list || !item) return;
    if (item.offsetTop < list.scrollTop) list.scrollTop = item.offsetTop;
    else if (item.offsetTop + item.offsetHeight > list.scrollTop + list.clientHeight) {
      list.scrollTop = item.offsetTop + item.offsetHeight - list.clientHeight;
    }
  }, [active, open]);
  useEffect(() => {
    if (!open) return;
    function outside(event: PointerEvent) {
      const target = event.target as Node;
      if (!trigger.current?.contains(target) && !menu.current?.contains(target)) setOpen(false);
    }
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [open]);
  function show() { setActive(selected); search.current = { text: "", time: 0 }; setOpen(true); }
  function choose(index: number) { onChange(options[index].value); setOpen(false); trigger.current?.focus(); }
  function keyDown(event: KeyboardEvent) {
    if (event.key === "Escape" && open) { event.preventDefault(); event.stopPropagation(); setOpen(false); return; }
    if (event.key === "Tab") { setOpen(false); return; }
    if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
      event.preventDefault();
      if (!open) show();
      setActive(event.key === "Home" ? 0 : event.key === "End" ? options.length - 1
        : Math.max(0, Math.min(options.length - 1, (open ? active : selected) + (event.key === "ArrowDown" ? 1 : -1))));
    } else if (event.key === "Enter" || event.key === " ") {
      event.preventDefault(); if (open) choose(active); else show();
    } else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
      event.preventDefault();
      const text = (Date.now() - search.current.time < 700 ? search.current.text : "") + event.key.toLowerCase();
      search.current = { text, time: Date.now() };
      const index = options.findIndex((option) => option.label.toLowerCase().startsWith(text));
      if (!open) show();
      search.current = { text, time: Date.now() };
      if (index >= 0) setActive(index);
    }
  }
  return <div className="custom-select">
    <label id={`${id}-label`} htmlFor={id}>{label}</label>
    <button ref={trigger} id={id} type="button" role="combobox" className="select-trigger" autoFocus={autoFocus}
      disabled={disabled || options.length === 0} aria-labelledby={`${id}-label`} aria-haspopup="listbox"
      aria-expanded={open} aria-controls={open ? `${id}-menu` : undefined}
      aria-activedescendant={open ? `${id}-option-${active}` : undefined}
      onClick={() => open ? setOpen(false) : show()} onKeyDown={keyDown} onBlur={() => setOpen(false)}>
      <span>{options[selected]?.label ?? "Select an option"}</span>
      <svg aria-hidden="true" viewBox="0 0 20 20"><path d="m5 7.5 5 5 5-5" /></svg>
    </button>
    {open && createPortal(<div ref={menu} id={`${id}-menu`} role="listbox" aria-labelledby={`${id}-label`}
      data-picker-owner={trigger.current?.closest(".date-time-field")?.getAttribute("data-picker") ?? undefined}
      className="select-menu" style={position} onPointerDown={(event) => event.preventDefault()}>
      {options.map((option, index) => <div key={option.value} id={`${id}-option-${index}`} role="option"
        aria-selected={option.value === value} data-index={index} data-active={active === index}
        className="select-option" onPointerMove={() => setActive(index)} onClick={() => choose(index)}>
        <span>{option.label}</span><span aria-hidden="true">{option.value === value ? "✓" : ""}</span>
      </div>)}
    </div>, document.body)}
  </div>;
}
