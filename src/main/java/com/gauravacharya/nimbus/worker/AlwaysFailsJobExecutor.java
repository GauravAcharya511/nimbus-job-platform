package com.gauravacharya.nimbus.worker;

import com.gauravacharya.nimbus.job.Job;
import org.springframework.stereotype.Component;

/** Deliberately fails, exercising the retry and dead-letter paths. */
@Component
public class AlwaysFailsJobExecutor implements JobExecutor {

    @Override
    public String type() { return "always-fails"; }

    @Override
    public void execute(Job job) {
        throw new IllegalStateException("intentional failure for retry testing");
    }
}
