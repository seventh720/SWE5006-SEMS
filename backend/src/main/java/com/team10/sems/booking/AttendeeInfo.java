package com.team10.sems.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contact details captured for this order, independently of the account profile. */
public record AttendeeInfo(
        @Size(max = AttendeeInfo.TEXT_MAX_LENGTH) String realName,
        @Email @Size(max = 255) String email,
        @Size(max = 30)
        @Pattern(regexp = AttendeeInfo.PHONE_PATTERN, message = "Enter a valid phone number") String phone,
        @Size(max = AttendeeInfo.TEXT_MAX_LENGTH) String studentId,
        @Size(max = AttendeeInfo.TEXT_MAX_LENGTH) String passportNumber,
        @Size(max = AttendeeInfo.CUSTOM_MAX_LENGTH) String customAnswer) {
    public static final int TEXT_MAX_LENGTH = 100;
    public static final int CUSTOM_MAX_LENGTH = 500;
    public static final String PHONE_PATTERN = "[+0-9() .-]{5,30}";
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
