package com.gauravacharya.nimbus.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A job lifecycle event.
 *
 * Deliberately carries identifiers and state rather than the job payload: events are
 * broadcast to any interested consumer, so keeping user data off the topic avoids
 * leaking it, and small events keep the topic cheap to retain and replay.
 */
public record JobEvent(
        UUID jobId,
        UUID userId,
        String jobType,
        JobEventType eventType,
        int attempts,
        String errorMessage,
        OffsetDateTime occurredAt
) {
    public static JobEvent of(UUID jobId, UUID userId, String jobType,
                              JobEventType eventType, int attempts, String errorMessage) {
        return new JobEvent(jobId, userId, jobType, eventType, attempts,
                errorMessage, OffsetDateTime.now());
    }
}
