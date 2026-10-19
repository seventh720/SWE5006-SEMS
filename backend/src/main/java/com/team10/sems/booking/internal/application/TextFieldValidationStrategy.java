package com.team10.sems.booking.internal.application;

record TextFieldValidationStrategy(int maxLength) implements BookingFieldValidationStrategy {
    @Override public boolean isValid(String value) { return value.length() <= maxLength; }
    @Override public String errorMessage(String label) { return label + " must be at most " + maxLength + " characters"; }
}
