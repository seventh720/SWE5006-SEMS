package com.team10.sems.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookingProfile(@Size(max = 100) String realName,
        @Email @Size(max = 255) String email,
        @Size(max = 30) @Pattern(regexp = "[+0-9() .-]{5,30}", message = "Enter a valid phone number") String phone,
        @Size(max = 100) String studentId, @Size(max = 100) String passportNumber) {
    public BookingProfile {
        realName = clean(realName);
        email = clean(email);
        phone = clean(phone);
        studentId = clean(studentId);
        passportNumber = clean(passportNumber);
    }

    @Override
    public String toString() { return "BookingProfile[redacted]"; }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
