package com.gauravacharya.nimbus.job.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Job {

    private UUID id;
    private String name;
    private JobType type;
    private JobStatus status;
    private JobPriority priority;
    private LocalDateTime createdAt;

    public Job() {
        this.id = UUID.randomUUID();
        this.status = JobStatus.CREATED;
        this.priority = JobPriority.MEDIUM;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JobType getType() {
        return type;
    }

    public JobStatus getStatus() {
        return status;
    }

    public JobPriority getPriority() {
        return priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(JobType type) {
        this.type = type;
    }

    public void setPriority(JobPriority priority) {
        this.priority = priority;
    }

    public void queue() {
        this.status = JobStatus.QUEUED;
    }

    public void start() {
        this.status = JobStatus.RUNNING;
    }

    public void complete() {
        this.status = JobStatus.COMPLETED;
    }

    public void fail() {
        this.status = JobStatus.FAILED;
    }

    public void retry() {
        this.status = JobStatus.RETRYING;
    }
}