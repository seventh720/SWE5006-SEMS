package com.team10.sems.event;

import java.util.UUID;

/** Synchronous validation in the event update transaction, with the event locked. */
public record EventCapacityChanging(UUID eventId, int capacity) { }
