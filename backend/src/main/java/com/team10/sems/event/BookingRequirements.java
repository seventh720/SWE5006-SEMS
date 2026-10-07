package com.team10.sems.event;

import jakarta.validation.constraints.Size;

/** Fields required once per order, configured before event publication. */
public record BookingRequirements(boolean realName, boolean email, boolean phone,
        boolean studentId, boolean passport, @Size(max = 100) String customFieldLabel) {
    public static final BookingRequirements NONE = new BookingRequirements(false, false, false, false, false, null);

    public BookingRequirements {
        customFieldLabel = customFieldLabel == null || customFieldLabel.isBlank() ? null : customFieldLabel.strip();
    }
}
