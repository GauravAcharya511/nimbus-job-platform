package com.gauravacharya.nimbus.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the worker on a fixed schedule.
 *
 * Kept separate from JobWorker so the polling trigger can be disabled
 * (nimbus.worker.scheduled=false) while the worker itself remains callable —
 * tests invoke poll() directly and must not race a background scheduler.
 */
@Component
@ConditionalOnProperty(name = "nimbus.worker.scheduled", havingValue = "true", matchIfMissing = true)
public class JobWorkerScheduler {

    private final JobWorker worker;

    public JobWorkerScheduler(JobWorker worker) { this.worker = worker; }

    @Scheduled(fixedDelayString = "${nimbus.worker.poll-interval-ms:200}")
    public void tick() { worker.poll(); }
}
