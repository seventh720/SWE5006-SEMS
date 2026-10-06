package com.team10.sems.ticketing.internal.application;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TicketTypeInput(

        @NotBlank
        @Size(max = 100)
        String name,

        @PositiveOrZero
        long priceMinor,

        @NotBlank
        @Pattern(regexp = "SGD", message = "Only SGD is supported")
        String currency,

        @Positive
        int quota,

        @PositiveOrZero
        Long version) {
}