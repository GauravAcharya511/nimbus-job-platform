package com.gauravacharya.nimbus.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Application metrics for the job pipeline.
 *
 * Counters answer "how much happened", the timer answers "how long did it take"
 * (and gives percentiles), and the queue-depth gauge is the leading indicator:
 * a rising backlog signals workers falling behind before latency degrades.
 */
@Component
public class JobMetrics {

    private final Counter submitted;
    private final Counter succeeded;
    private final Counter failed;
    private final Counter retried;
    private final Counter deadLettered;
    private final Counter cancelled;
    private final Counter rateLimited;
    private final Timer executionTimer;

    private final AtomicLong queueDepth = new AtomicLong();

    public JobMetrics(MeterRegistry registry) {
        this.submitted = Counter.builder("nimbus.jobs.submitted")
                .description("Jobs accepted for execution").register(registry);
        this.succeeded = Counter.builder("nimbus.jobs.completed")
                .tag("outcome", "succeeded").register(registry);
        this.failed = Counter.builder("nimbus.jobs.completed")
                .tag("outcome", "failed").register(registry);
        this.retried = Counter.builder("nimbus.jobs.retried")
                .description("Attempts that failed and were rescheduled").register(registry);
        this.deadLettered = Counter.builder("nimbus.jobs.dead_lettered")
                .description("Jobs that exhausted their retry budget").register(registry);
        this.cancelled = Counter.builder("nimbus.jobs.cancelled")
                .description("Jobs cancelled before execution").register(registry);
        this.rateLimited = Counter.builder("nimbus.requests.rate_limited")
                .description("Requests rejected by the token bucket").register(registry);

        this.executionTimer = Timer.builder("nimbus.job.execution")
                .description("Time spent executing a job")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        Gauge.builder("nimbus.queue.depth", queueDepth, AtomicLong::get)
                .description("Jobs waiting to be claimed")
                .register(registry);
    }

    public void recordSubmitted() { submitted.increment(); }
    public void recordSucceeded(Duration took) { succeeded.increment(); executionTimer.record(took); }
    public void recordFailed(Duration took) { failed.increment(); executionTimer.record(took); }
    public void recordRetry() { retried.increment(); }
    public void recordDeadLettered() { deadLettered.increment(); }
    public void recordCancelled() { cancelled.increment(); }
    public void recordRateLimited() { rateLimited.increment(); }
    public void setQueueDepth(long depth) { queueDepth.set(depth); }
}
