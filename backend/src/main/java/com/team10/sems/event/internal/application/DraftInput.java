package com.team10.sems.event.internal.application;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record DraftInput(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotBlank @Size(max = 500) String location,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull @Positive Integer capacity,
        @PositiveOrZero Long version) {
}
