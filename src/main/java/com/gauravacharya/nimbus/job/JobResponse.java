package com.gauravacharya.nimbus.job;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id, String type, String payload, JobStatus status,
        int attempts, int maxAttempts, String errorMessage,
        OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime completedAt,
        OffsetDateTime nextAttemptAt, String cronExpression, UUID parentJobId
) {
    public static JobResponse from(Job j) {
        return new JobResponse(j.getId(), j.getType(), j.getPayload(), j.getStatus(),
                j.getAttempts(), j.getMaxAttempts(), j.getErrorMessage(),
                j.getCreatedAt(), j.getStartedAt(), j.getCompletedAt(),
                j.getNextAttemptAt(), j.getCronExpression(), j.getParentJobId());
    }
}