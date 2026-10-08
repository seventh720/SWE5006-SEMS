import { Select } from "../../shared/components/Select";
import { useEffect, useRef, useState } from "react";
import { validSingaporeInput } from "./drafts";

const months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
const weekdays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const pad = (value: number) => String(value).padStart(2, "0");

/** App-rendered English calendar, independent of the browser's native picker locale. */
export function DateTimeField({ name, label, value, onChange, error, required = true, after, before, atOrBefore }: {
  name: string; label: string; value: string; onChange: (value: string) => void; error?: string; required?: boolean; after?: string; before?: string; atOrBefore?: string;
}) {
  const minimum = after && validSingaporeInput(after)
    ? new Date(new Date(`${after}:00Z`).getTime() + 60000).toISOString().slice(0, 16) : "";
  const maximum = before && validSingaporeInput(before)
    ? new Date(new Date(`${before}:00Z`).getTime() - 60000).toISOString().slice(0, 16)
    : atOrBefore && validSingaporeInput(atOrBefore) ? atOrBefore : "";
  const inRange = (text: string) => (!minimum || text >= minimum) && (!maximum || text <= maximum);
  const parse = (text: string) => ({ year: Number(text.slice(0, 4)), month: Number(text.slice(5, 7)) - 1,
    day: Number(text.slice(8, 10)), hour: Number(text.slice(11, 13)), minute: Number(text.slice(14, 16)) });
  const format = (item: ReturnType<typeof parse>) => `${String(item.year).padStart(4, "0")}-${pad(item.month + 1)}-${pad(item.day)}T${pad(item.hour)}:${pad(item.minute)}`;
  const fieldError = error || (validSingaporeInput(value) && !inRange(value) ? "Choose a time within the allowed range." : undefined);
  const field = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [date, setDate] = useState({ year: 2026, month: 0, day: 1, hour: 0, minute: 0 });
  useEffect(() => {
    if (!open) return;
    function dismiss(event: PointerEvent) {
      if (event.target instanceof Element && !field.current?.contains(event.target)
          && event.target.closest("[data-picker-owner]")?.getAttribute("data-picker-owner") !== name) setOpen(false);
    }
    document.addEventListener("pointerdown", dismiss);
    return () => document.removeEventListener("pointerdown", dismiss);
  }, [open, name]);
  const days = new Date(Date.UTC(date.year, date.month + 1, 0)).getUTCDate();
  const offset = new Date(Date.UTC(date.year, date.month, 1)).getUTCDay();
  function close() { setOpen(false); trigger.current?.focus(); }
  function show() {
    const initial = validSingaporeInput(value) && inRange(value) ? value
      : minimum || maximum || new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 16);
    setDate(parse(initial));
    setOpen(true);
  }
  function selectDate(next: typeof date) {
    setDate(minimum && format(next) < minimum ? parse(minimum)
      : maximum && format(next) > maximum ? parse(maximum) : next);
  }
  const sameMinimumDay = !!minimum && format(date).slice(0, 10) === minimum.slice(0, 10);
  const minimumHour = sameMinimumDay ? Number(minimum.slice(11, 13)) : 0;
  const minimumMinute = sameMinimumDay && date.hour === minimumHour ? Number(minimum.slice(14, 16)) : 0;
  const sameMaximumDay = !!maximum && format(date).slice(0, 10) === maximum.slice(0, 10);
  const maximumHour = sameMaximumDay ? Number(maximum.slice(11, 13)) : 23;
  const maximumMinute = sameMaximumDay && date.hour === maximumHour ? Number(maximum.slice(14, 16)) : 59;
  function changeMonth(year: number, month: number) {
    if (year < 100 || year > 9999) return;
    selectDate({ ...date, year, month, day: Math.min(date.day, new Date(Date.UTC(year, month + 1, 0)).getUTCDate()) });
  }
  return <div ref={field} data-picker={name} className="date-time-field"
    onBlur={(event) => { if (event.relatedTarget instanceof Node && !event.currentTarget.contains(event.relatedTarget)) setOpen(false); }}>
    <label htmlFor={name}>{label}</label>
    <div className="date-time-controls">
      <input id={name} type="text" placeholder="YYYY-MM-DD HH:mm" maxLength={16} required={required}
        pattern="[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}"
        value={value.replace("T", " ")} onChange={(event) => onChange(event.target.value.replace(" ", "T"))}
        aria-invalid={!!fieldError} aria-describedby={`event-time-zone${fieldError ? ` ${name}-error` : ""}`} />
      <button ref={trigger} type="button" className="secondary-button" aria-label={`Choose ${label.toLowerCase()}`}
        aria-expanded={open} aria-controls={`${name}-calendar`} onClick={() => open ? close() : show()}>Calendar</button>
    </div>
    {open && <section id={`${name}-calendar`} className="english-calendar" aria-label={`${label} calendar`} lang="en"
      onKeyDown={(event) => { if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); close(); } }}>
      <div className="calendar-month">
        <button type="button" className="secondary-button" aria-label="Previous month" disabled={(date.year === 100 && date.month === 0) || (!!minimum && format(date).slice(0, 7) <= minimum.slice(0, 7))}
          onClick={() => changeMonth(date.month === 0 ? date.year - 1 : date.year, (date.month + 11) % 12)}>‹</button>
        <Select label="Month" autoFocus value={String(date.month)} options={months.map((label, value) => ({ label, value: String(value) }))}
          onChange={(value) => changeMonth(date.year, Number(value))} />
        <label>Year<input type="number" min="100" max="9999" value={date.year} onChange={(event) => changeMonth(Number(event.target.value), date.month)} /></label>
        <button type="button" className="secondary-button" aria-label="Next month" disabled={(date.year === 9999 && date.month === 11) || (!!maximum && format(date).slice(0, 7) >= maximum.slice(0, 7))}
          onClick={() => changeMonth(date.month === 11 ? date.year + 1 : date.year, (date.month + 1) % 12)}>›</button>
      </div>
      <div className="calendar-days">
        {weekdays.map((day) => <span key={day} className="calendar-weekday">{day}</span>)}
        {Array.from({ length: offset }, (_, index) => <span key={`empty-${index}`} />)}
        {Array.from({ length: days }, (_, index) => index + 1).map((day) => <button key={day} type="button"
          aria-label={`${months[date.month]} ${day}, ${date.year}`} aria-pressed={day === date.day}
          disabled={(!!minimum && format({ ...date, day }).slice(0, 10) < minimum.slice(0, 10))
            || (!!maximum && format({ ...date, day }).slice(0, 10) > maximum.slice(0, 10))}
          onClick={() => selectDate({ ...date, day })}>{day}</button>)}
      </div>
      <div className="calendar-time">
        <Select label="Hour" value={String(date.hour)} options={Array.from({ length: 24 }, (_, value) => ({ value: String(value), label: pad(value) })).filter((option) => Number(option.value) >= minimumHour && Number(option.value) <= maximumHour)}
          onChange={(value) => selectDate({ ...date, hour: Number(value) })} />
        <Select label="Minute" value={String(date.minute)} options={Array.from({ length: 60 }, (_, value) => ({ value: String(value), label: pad(value) })).filter((option) => Number(option.value) >= minimumMinute && Number(option.value) <= maximumMinute)}
          onChange={(value) => selectDate({ ...date, minute: Number(value) })} /><span>SGT · 24-hour</span>
      </div>
      <div className="button-row"><button type="button" className="primary-button compact" disabled={!inRange(format(date))} onClick={() => {
        onChange(format(date)); close();
      }}>Apply</button><button type="button" className="secondary-button" onClick={close}>Cancel</button></div>
    </section>}
    {fieldError && <span className="field-error" id={`${name}-error`}>{fieldError}</span>}
  </div>;
}
