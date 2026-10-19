package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.AttendeeInfo;
import jakarta.validation.Validator;

final class EmailFieldValidationStrategy implements BookingFieldValidationStrategy {
    private final Validator validator;
    EmailFieldValidationStrategy(Validator validator) { this.validator = validator; }

    @Override public boolean isValid(String value) {
        // Reuse @Email and @Size semantics at the HTTP boundary; no competing email regex.
        return validator.validateValue(AttendeeInfo.class, "email", value).isEmpty();
    }
    @Override public String errorMessage(String label) { return "Enter a valid email address of at most 255 characters"; }
}
