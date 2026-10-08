package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.AttendeeInfo;
import com.team10.sems.event.BookingRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class BookingInfoValidator {
    private BookingInfoValidator() { }

    static void validate(BookingRequirements requirements,
            AttendeeInfo info) {
        checkField(requirements.realName(), info.realName(), "Real name");
        checkField(requirements.email(), info.email(), "Email");
        checkField(requirements.phone(), info.phone(), "Phone");
        checkField(requirements.studentId(), info.studentId(), "Student ID number");
        checkField(requirements.passport(), info.passportNumber(), "Passport number");
        checkField(requirements.customFieldLabel() != null, info.customAnswer(), "Custom information");
    }

    private static void checkField(boolean required, String value, String label) {
        if (required && value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required for this event");
        }
        if (!required && value != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is not requested for this event");
        }
    }

}
