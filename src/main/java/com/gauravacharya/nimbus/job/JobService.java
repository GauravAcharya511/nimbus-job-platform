package com.gauravacharya.nimbus.job;

import com.gauravacharya.nimbus.events.JobEvent;
import com.gauravacharya.nimbus.metrics.JobMetrics;
import com.gauravacharya.nimbus.events.JobEventPublisher;
import com.gauravacharya.nimbus.events.JobEventType;
import com.gauravacharya.nimbus.security.CurrentUser;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository repository;
    private final JobEventPublisher events;
    private final JobMetrics metrics;

    public JobService(JobRepository repository, JobEventPublisher events, JobMetrics metrics) {
        this.repository = repository;
        this.events = events;
        this.metrics = metrics;
    }

    @CacheEvict(value = "jobs", allEntries = true)
    @Transactional
    public JobResponse submit(CreateJobRequest request) {
        Job job = new Job();
        job.setType(request.type());
        job.setPayload(request.payload());
        job.setStatus(JobStatus.PENDING);
        job.setUserId(CurrentUser.id());

        if (request.cronExpression() != null && !request.cronExpression().isBlank()) {
            String cron = request.cronExpression().trim();
            if (!CronExpression.isValidExpression(cron)) {
                throw new InvalidCronException(cron);
            }
            job.setCronExpression(cron);
            // First occurrence is the next time the expression fires.
            job.setNextAttemptAt(nextOccurrence(cron, OffsetDateTime.now()));
        } else {
            job.setNextAttemptAt(
                    request.scheduledAt() != null ? request.scheduledAt() : OffsetDateTime.now());
        }

        Job saved = repository.save(job);
        events.publish(JobEvent.of(saved.getId(), saved.getUserId(), saved.getType(),
                JobEventType.SUBMITTED, saved.getAttempts(), null));
        metrics.recordSubmitted();
        return JobResponse.from(saved);
    }

    public static OffsetDateTime nextOccurrence(String cron, OffsetDateTime from) {
        var next = CronExpression.parse(cron).next(from.toZonedDateTime());
        if (next == null) {
            throw new InvalidCronException(cron);
        }
        return next.toOffsetDateTime();
    }

    /**
     * Cancels a job. A running job is left alone: it is already executing and
     * interrupting it mid-flight would leave its side effects half-applied.
     * Cancelling any occurrence of a recurring job stops the whole schedule.
     */
    @Transactional
    public JobResponse cancel(UUID id) {
        Job job = repository.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> new JobNotFoundException(id));

        if (job.getStatus() == JobStatus.RUNNING) {
            throw new JobNotCancellableException(id, job.getStatus());
        }
        if (job.getStatus() == JobStatus.SUCCEEDED || job.getStatus() == JobStatus.FAILED) {
            // terminal already; cancelling is only meaningful for the schedule itself
            if (job.getCronExpression() == null) {
                throw new JobNotCancellableException(id, job.getStatus());
            }
        }

        job.setStatus(JobStatus.CANCELLED);
        job.setCompletedAt(OffsetDateTime.now());
        Job saved = repository.save(job);

        events.publish(JobEvent.of(saved.getId(), saved.getUserId(), saved.getType(),
                JobEventType.CANCELLED, saved.getAttempts(), null));
        metrics.recordCancelled();
        return JobResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> findAll(Pageable pageable) {
        return repository.findByUserId(CurrentUser.id(), pageable).map(JobResponse::from);
    }

    @Cacheable(value = "jobs", key = "#id")
    @Transactional(readOnly = true)
    public JobResponse findById(UUID id) {
        return repository.findByIdAndUserId(id, CurrentUser.id())
                .map(JobResponse::from)
                .orElseThrow(() -> new JobNotFoundException(id));
    }
}