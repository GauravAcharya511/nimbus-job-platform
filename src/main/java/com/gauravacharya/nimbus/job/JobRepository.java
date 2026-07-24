package com.gauravacharya.nimbus.job;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findByUserId(UUID userId, Pageable pageable);

    Optional<Job> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Claims due jobs for execution.
     *
     * PESSIMISTIC_WRITE issues SELECT ... FOR UPDATE, and the -2 lock timeout is
     * Hibernate's constant for SKIP LOCKED: Postgres skips rows already locked by
     * another worker rather than blocking on them. Multiple workers therefore
     * partition the queue with no coordination and never execute the same job twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
           SELECT j FROM Job j
           WHERE j.status = com.gauravacharya.nimbus.job.JobStatus.PENDING
             AND j.nextAttemptAt <= :now
           ORDER BY j.nextAttemptAt ASC
           """)
    List<Job> claimDueJobs(@Param("now") OffsetDateTime now, Pageable pageable);

    /**
     * True if any job in a recurring series has been cancelled. Cancelling one
     * occurrence stops the whole schedule from producing further runs.
     */
    @Query("""
           SELECT COUNT(j) > 0 FROM Job j
           WHERE j.status = com.gauravacharya.nimbus.job.JobStatus.CANCELLED
             AND (j.id = :seriesId OR j.parentJobId = :seriesId)
           """)
    boolean existsCancelledInSeries(@Param("seriesId") UUID seriesId);

    /** Jobs due for execution right now — the backlog the workers are draining. */
    @Query("""
           SELECT COUNT(j) FROM Job j
           WHERE j.status = com.gauravacharya.nimbus.job.JobStatus.PENDING
             AND j.nextAttemptAt <= :now
           """)
    long countClaimable(@Param("now") OffsetDateTime now);
}