package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.AttendeeInfo;
import com.team10.sems.event.BookingRequirements;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class BookingInfoValidatorTest {
    private static final jakarta.validation.ValidatorFactory FACTORY = jakarta.validation.Validation.buildDefaultValidatorFactory();
    private final BookingInfoValidator infoValidator = new BookingInfoValidator(FACTORY.getValidator());
    @org.junit.jupiter.api.AfterAll static void closeValidatorFactory() { FACTORY.close(); }
    private BookingRequirements required(int field) {
        return new BookingRequirements(field == 0, field == 1, field == 2,
                field == 3, field == 4, field == 5 ? "Department" : null);
    }

    private AttendeeInfo info(int field, String value) {
        String[] fields = new String[6];
        fields[field] = value;
        return new AttendeeInfo(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void requiresEachSelectedFieldAndRejectsWhitespace(int field) {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> infoValidator.validate(required(field), info(field, "  "))).getStatusCode().value());
        assertDoesNotThrow(() -> infoValidator.validate(required(field), info(field, field == 1 ? "person@example.test" : field == 2 ? "+65 8123 4567" : "value")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void rejectsUnrequestedPersonalInformation(int field) {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> infoValidator.validate(BookingRequirements.NONE, info(field, "value"))).getStatusCode().value());
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> invalidFields() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(0, "x".repeat(101)),
                org.junit.jupiter.params.provider.Arguments.of(1, "not-an-email"),
                org.junit.jupiter.params.provider.Arguments.of(1, "x".repeat(250) + "@example.test"),
                org.junit.jupiter.params.provider.Arguments.of(2, "call me"),
                org.junit.jupiter.params.provider.Arguments.of(2, "1".repeat(31)),
                org.junit.jupiter.params.provider.Arguments.of(3, "x".repeat(101)),
                org.junit.jupiter.params.provider.Arguments.of(4, "x".repeat(101)),
                org.junit.jupiter.params.provider.Arguments.of(5, "x".repeat(501)));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidFields")
    void selectedStrategyRejectsMalformedValuesConsistentlyWithHttpValidation(int field, String value) {
        var details = info(field, value);
        assertFalse(FACTORY.getValidator().validate(details).isEmpty());
        var error = assertThrows(ResponseStatusException.class, () -> infoValidator.validate(required(field), details));
        assertEquals(400, error.getStatusCode().value());
        String reason = java.util.Objects.requireNonNull(error.getReason(), "Validation error must include a reason");
        assertFalse(reason.contains(value)); // Do not echo personal information in validation errors.
    }

    @org.junit.jupiter.api.Test
    void validInternationalContactAndTextBoundariesRemainAccepted() {
        var details = new AttendeeInfo("张三 O'Connor", "person+event@example.test", "+65 (8123) 4567",
                "s".repeat(100), "p".repeat(100), "d".repeat(500));
        assertTrue(FACTORY.getValidator().validate(details).isEmpty());
        assertDoesNotThrow(() -> infoValidator.validate(new BookingRequirements(true, true, true, true, true, "Department"), details));
        assertDoesNotThrow(() -> infoValidator.validate(BookingRequirements.NONE, AttendeeInfo.EMPTY));
    }

}
