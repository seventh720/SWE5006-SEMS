package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.AttendeeInfo;
import com.team10.sems.event.BookingRequirements;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class BookingInfoValidatorTest {
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
                () -> BookingInfoValidator.validate(required(field), info(field, "  "))).getStatusCode().value());
        assertDoesNotThrow(() -> BookingInfoValidator.validate(required(field), info(field, "value")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void rejectsUnrequestedPersonalInformation(int field) {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> BookingInfoValidator.validate(BookingRequirements.NONE, info(field, "value"))).getStatusCode().value());
    }
}
