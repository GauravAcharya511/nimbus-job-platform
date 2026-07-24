package com.gauravacharya.nimbus.job;

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

    public JobService(JobRepository repository) { this.repository = repository; }

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

        return JobResponse.from(repository.save(job));
    }

    public static OffsetDateTime nextOccurrence(String cron, OffsetDateTime from) {
        var next = CronExpression.parse(cron).next(from.toZonedDateTime());
        if (next == null) {
            throw new InvalidCronException(cron);
        }
        return next.toOffsetDateTime();
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