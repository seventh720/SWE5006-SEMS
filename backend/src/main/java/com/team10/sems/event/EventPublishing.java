package com.team10.sems.event;

import java.util.UUID;

/** Validated synchronously within the publication transaction. */
public record EventPublishing(UUID eventId) { }
