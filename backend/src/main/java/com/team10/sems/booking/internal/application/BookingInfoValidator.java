package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.AttendeeInfo;
import com.team10.sems.event.BookingRequirements;
import jakarta.validation.Validator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Strategy context shared by bookings and pre-registrations, including non-HTTP callers. */
@Component
final class BookingInfoValidator {
    private record Field(String label, Predicate<BookingRequirements> required,
            Function<AttendeeInfo, String> value, BookingFieldValidationStrategy strategy) { }
    private final List<Field> fields;

    BookingInfoValidator(Validator validator) {
        var text = new TextFieldValidationStrategy(AttendeeInfo.TEXT_MAX_LENGTH);
        fields = List.of(
                new Field("Real name", BookingRequirements::realName, AttendeeInfo::realName, text),
                new Field("Email", BookingRequirements::email, AttendeeInfo::email, new EmailFieldValidationStrategy(validator)),
                new Field("Phone", BookingRequirements::phone, AttendeeInfo::phone, new PhoneFieldValidationStrategy()),
                new Field("Student ID number", BookingRequirements::studentId, AttendeeInfo::studentId, text),
                new Field("Passport number", BookingRequirements::passport, AttendeeInfo::passportNumber, text),
                new Field("Custom information", requirements -> requirements.customFieldLabel() != null,
                        AttendeeInfo::customAnswer, new TextFieldValidationStrategy(AttendeeInfo.CUSTOM_MAX_LENGTH)));
    }

    void validate(BookingRequirements requirements, AttendeeInfo info) {
        // Keep event-specific presence/privacy checks independent of format algorithms.
        for (var field : fields) {
            String value = field.value().apply(info);
            boolean required = field.required().test(requirements);
            if (required && value == null) reject(field.label() + " is required for this event");
            if (!required && value != null) reject(field.label() + " is not requested for this event");
        }
        for (var field : fields) {
            String value = field.value().apply(info);
            if (value != null && !field.strategy().isValid(value)) reject(field.strategy().errorMessage(field.label()));
        }
    }

    private static void reject(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
