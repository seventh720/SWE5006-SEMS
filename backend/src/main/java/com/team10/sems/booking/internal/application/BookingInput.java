package com.team10.sems.booking.internal.application;

import com.team10.sems.booking.AttendeeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BookingInput(@NotNull UUID eventId, @NotNull UUID ticketTypeId,
        @Min(1) @Max(10) int quantity, @Valid AttendeeInfo attendeeInfo) {
    public BookingInput {
        attendeeInfo = attendeeInfo == null ? AttendeeInfo.EMPTY : attendeeInfo;
    }
}
