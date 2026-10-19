package com.team10.sems.booking.internal.application;

/** Validates a supplied, normalized value. Required/forbidden-field rules belong to the context. */
interface BookingFieldValidationStrategy {
    boolean isValid(String value);
    String errorMessage(String label);
}
