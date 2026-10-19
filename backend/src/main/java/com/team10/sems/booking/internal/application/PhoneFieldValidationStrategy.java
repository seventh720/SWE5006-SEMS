package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.AttendeeInfo;
import java.util.regex.Pattern;

final class PhoneFieldValidationStrategy implements BookingFieldValidationStrategy {
    private static final Pattern PHONE = Pattern.compile(AttendeeInfo.PHONE_PATTERN);
    @Override public boolean isValid(String value) { return PHONE.matcher(value).matches(); }
    @Override public String errorMessage(String label) { return "Enter a valid phone number"; }
}
