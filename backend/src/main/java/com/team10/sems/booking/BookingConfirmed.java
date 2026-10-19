package com.team10.sems.booking;
import java.util.UUID;
public record BookingConfirmed(UUID bookingId, UUID eventId) { }
