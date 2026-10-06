import { apiRequest } from "../../shared/api/client";

export interface ProfileInfo {
  realName?: string | null;
  email?: string | null;
  phone?: string | null;
  studentId?: string | null;
  passportNumber?: string | null;
}

export const personalFields = [
  { key: "realName", label: "Real name", max: 100, type: "text", autoComplete: "name" },
  { key: "email", label: "Contact email", max: 255, type: "email", autoComplete: "email" },
  { key: "phone", label: "Phone", max: 30, type: "tel", autoComplete: "tel" },
  { key: "studentId", label: "Student ID number", max: 100, type: "text", autoComplete: "off" },
  { key: "passportNumber", label: "Passport number", max: 100, type: "text", autoComplete: "off" },
] as const;

export function readProfile(token: string, signal?: AbortSignal) {
  return apiRequest<ProfileInfo>("/api/v1/profile", { signal }, token);
}

export function saveProfile(token: string, info: ProfileInfo) {
  return apiRequest<ProfileInfo>("/api/v1/profile", { method: "PUT", body: JSON.stringify(info) }, token);
}
