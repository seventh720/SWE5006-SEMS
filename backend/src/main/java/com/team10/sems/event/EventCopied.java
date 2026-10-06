package com.team10.sems.event;

import java.util.UUID;

/** Synchronous copy of configuration into a newly saved draft. */
public record EventCopied(UUID sourceId, UUID draftId) { }
