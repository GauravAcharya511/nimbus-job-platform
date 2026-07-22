package com.gauravacharya.nimbus.worker;

import com.gauravacharya.nimbus.job.Job;
import com.gauravacharya.nimbus.job.JobRepository;
import com.gauravacharya.nimbus.job.JobService;
import com.gauravacharya.nimbus.job.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Claims due jobs and executes them.
 *
 * Claiming and executing run in separate transactions: the claim marks jobs RUNNING
 * under a row lock and commits immediately, so locks are not held for the duration
 * of the work. Self-invocation goes through the proxy so @Transactional applies.
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobRepository jobs;
    private final JobExecutorRegistry registry;
    private final ApplicationContext context;
    private final int batchSize;

    public JobWorker(JobRepository jobs, JobExecutorRegistry registry, ApplicationContext context,
                     @Value("${nimbus.worker.batch-size:50}") int batchSize) {
        this.jobs = jobs;
        this.registry = registry;
        this.context = context;
        this.batchSize = batchSize;
    }

    private JobWorker self() { return context.getBean(JobWorker.class); }

    public void poll() {
        for (UUID id : self().claimBatch()) {
            self().runJob(id);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> claimBatch() {
        List<Job> due = jobs.claimDueJobs(OffsetDateTime.now(), PageRequest.of(0, batchSize));
        for (Job job : due) {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
        }
        return jobs.saveAll(due).stream().map(Job::getId).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runJob(UUID jobId) {
        Job job = jobs.findById(jobId).orElse(null);
        if (job == null) return;

        try {
            JobExecutor executor = registry.forType(job.getType())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No executor registered for type: " + job.getType()));
            executor.execute(job);
            markSucceeded(job);
        } catch (Exception e) {
            markFailedOrRetry(job, e);
        }
        jobs.save(job);

        if (job.getStatus() == JobStatus.SUCCEEDED && job.getCronExpression() != null) {
            scheduleNextOccurrence(job);
        }
    }

    /**
     * Enqueues the following occurrence of a recurring job. Each run is a distinct
     * row linked to the originating job, so execution history is preserved rather
     * than overwritten.
     */
    private void scheduleNextOccurrence(Job completed) {
        OffsetDateTime next = JobService.nextOccurrence(
                completed.getCronExpression(), OffsetDateTime.now());

        Job occurrence = new Job();
        occurrence.setType(completed.getType());
        occurrence.setPayload(completed.getPayload());
        occurrence.setUserId(completed.getUserId());
        occurrence.setStatus(JobStatus.PENDING);
        occurrence.setCronExpression(completed.getCronExpression());
        occurrence.setMaxAttempts(completed.getMaxAttempts());
        occurrence.setNextAttemptAt(next);
        occurrence.setParentJobId(
                completed.getParentJobId() != null ? completed.getParentJobId() : completed.getId());

        jobs.save(occurrence);
        log.info("recurring job {} scheduled next occurrence at {}", completed.getId(), next);
    }

    private void markSucceeded(Job job) {
        job.setStatus(JobStatus.SUCCEEDED);
        job.setCompletedAt(OffsetDateTime.now());
        job.setErrorMessage(null);
    }

    private void markFailedOrRetry(Job job, Exception e) {
        int attempts = job.getAttempts() + 1;
        job.setAttempts(attempts);
        job.setErrorMessage(e.getMessage());

        if (attempts >= job.getMaxAttempts()) {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(OffsetDateTime.now());
            log.warn("job {} exhausted {} attempts, dead-lettering", job.getId(), attempts);
        } else {
            long backoffSeconds = (long) Math.pow(2, attempts);
            job.setStatus(JobStatus.PENDING);
            job.setNextAttemptAt(OffsetDateTime.now().plusSeconds(backoffSeconds));
            log.info("job {} attempt {} failed, retrying in {}s", job.getId(), attempts, backoffSeconds);
        }
    }
}