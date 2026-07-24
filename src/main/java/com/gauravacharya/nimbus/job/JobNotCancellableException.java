package com.gauravacharya.nimbus.job;

import java.util.UUID;

public class JobNotCancellableException extends RuntimeException {
    public JobNotCancellableException(UUID id, JobStatus status) {
        super("Job " + id + " cannot be cancelled from status " + status);
    }
}
