package com.team10.sems.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contact details captured for this order, independently of the account profile. */
public record AttendeeInfo(
        @Size(max = 100) String realName,
        @Email @Size(max = 255) String email,
        @Size(max = 30)
        @Pattern(regexp = "[+0-9() .-]{5,30}", message = "Enter a valid phone number") String phone,
        @Size(max = 100) String studentId,
        @Size(max = 100) String passportNumber,
        @Size(max = 500) String customAnswer) {
    public static final AttendeeInfo EMPTY = new AttendeeInfo(null, null, null, null, null, null);

    public AttendeeInfo {
        realName = clean(realName);
        email = clean(email);
        phone = clean(phone);
        studentId = clean(studentId);
        passportNumber = clean(passportNumber);
        customAnswer = clean(customAnswer);
    }

    @Override
    public String toString() { return "AttendeeInfo[redacted]"; }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
