package com.team10.sems.event;

import java.util.UUID;

/** Synchronous cancellation in the same transaction as the event status change. */
public record EventCancelled(UUID eventId) { }
