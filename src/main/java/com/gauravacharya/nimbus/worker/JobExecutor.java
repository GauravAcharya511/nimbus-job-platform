package com.gauravacharya.nimbus.worker;

import com.gauravacharya.nimbus.job.Job;

/** Strategy for executing one kind of job. Implementations are discovered by Spring. */
public interface JobExecutor {

    /** The job type this executor handles, matching Job#getType(). */
    String type();

    /** Executes the job. Throwing marks the attempt failed and schedules a retry. */
    void execute(Job job) throws Exception;
}
