package com.gauravacharya.nimbus.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotBlank(message = "type is required")
        @Size(max = 100, message = "type must be at most 100 characters")
        String type,
        String payload
) {}
