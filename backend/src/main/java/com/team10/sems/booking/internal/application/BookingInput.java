package com.team10.sems.booking.internal.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BookingInput(@NotNull UUID eventId, @NotNull UUID ticketTypeId,
        @Min(1) @Max(10) int quantity) { }
