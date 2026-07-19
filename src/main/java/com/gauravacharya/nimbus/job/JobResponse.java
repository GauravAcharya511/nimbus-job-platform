package com.gauravacharya.nimbus.job;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id, String type, String payload, JobStatus status,
        int attempts, int maxAttempts, String errorMessage,
        OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime completedAt
) {
    static JobResponse from(Job j) {
        return new JobResponse(j.getId(), j.getType(), j.getPayload(), j.getStatus(),
                j.getAttempts(), j.getMaxAttempts(), j.getErrorMessage(),
                j.getCreatedAt(), j.getStartedAt(), j.getCompletedAt());
    }
}
