package com.team10.sems.booking;

import java.util.UUID;

/** Synchronous event published while the activity lock and cancellation transaction are held. */
public record BookingCancelled(UUID bookingId, UUID eventId, String reason) { }
