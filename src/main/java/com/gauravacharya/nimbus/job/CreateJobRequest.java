package com.gauravacharya.nimbus.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * @param scheduledAt     run no earlier than this instant; defaults to now
 * @param cronExpression  Spring cron expression for a recurring job; null runs once
 */
public record CreateJobRequest(
        @NotBlank(message = "type is required")
        @Size(max = 100, message = "type must be at most 100 characters")
        String type,

        String payload,

        OffsetDateTime scheduledAt,

        @Size(max = 120, message = "cronExpression must be at most 120 characters")
        String cronExpression
) {}