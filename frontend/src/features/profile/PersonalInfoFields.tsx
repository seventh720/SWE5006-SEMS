import { personalFields, type ProfileInfo } from "./profile";

export function PersonalInfoFields({ value, onChange, fields, required = false }: {
  value: ProfileInfo;
  onChange: (value: ProfileInfo) => void;
  fields?: (keyof ProfileInfo)[];
  required?: boolean;
}) {
  return <>{personalFields.filter((field) => !fields || fields.includes(field.key)).map((field) =>
    <label key={field.key}>{field.label}
      <input required={required} maxLength={field.max} type={field.type} autoComplete={field.autoComplete}
        minLength={field.key === "phone" ? 5 : undefined}
        pattern={field.key === "phone" ? "[+0-9\\(\\) .\\-]{5,30}" : undefined}
        value={value[field.key] ?? ""} onChange={(event) => onChange({ ...value, [field.key]: event.target.value })} />
    </label>)}</>;
}
