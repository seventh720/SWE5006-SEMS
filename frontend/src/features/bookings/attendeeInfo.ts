import type { ProfileInfo } from "../profile/profile";

export interface BookingRequirements {
  realName: boolean;
  email: boolean;
  phone: boolean;
  studentId: boolean;
  passport: boolean;
  customFieldLabel?: string | null;
}

export const noBookingRequirements: BookingRequirements = {
  realName: false, email: false, phone: false, studentId: false, passport: false, customFieldLabel: null,
};

export interface AttendeeInfo extends ProfileInfo {
  customAnswer?: string | null;
}

export function requiredPersonalFields(requirements: BookingRequirements): (keyof ProfileInfo)[] {
  const fields: (keyof ProfileInfo)[] = [];
  if (requirements.realName) fields.push("realName");
  if (requirements.email) fields.push("email");
  if (requirements.phone) fields.push("phone");
  if (requirements.studentId) fields.push("studentId");
  if (requirements.passport) fields.push("passportNumber");
  return fields;
}
