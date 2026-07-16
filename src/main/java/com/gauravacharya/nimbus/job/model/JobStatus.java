package com.gauravacharya.nimbus.job.model;

public enum JobStatus {
    CREATED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING,
    CANCELLED
}