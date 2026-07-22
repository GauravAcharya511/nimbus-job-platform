package com.gauravacharya.nimbus.worker;

import com.gauravacharya.nimbus.job.Job;
import com.gauravacharya.nimbus.job.JobRepository;
import com.gauravacharya.nimbus.job.JobStatus;
import com.gauravacharya.nimbus.support.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestContainer.class)
class JobWorkerTest {

    @Autowired JobRepository jobs;
    @Autowired JobWorker worker;

    @BeforeEach
    void clean() { jobs.deleteAll(); }

    private Job newJob(String type) {
        Job j = new Job();
        j.setType(type);
        j.setStatus(JobStatus.PENDING);
        j.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        return jobs.save(j);
    }

    @Test
    @DisplayName("a successful job reaches SUCCEEDED")
    void successfulJobSucceeds() {
        UUID id = newJob("echo").getId();
        worker.poll();
        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("a failing job is retried with backoff, then dead-lettered")
    void failingJobRetriesThenFails() {
        UUID id = newJob("always-fails").getId();

        worker.poll();
        Job after1 = jobs.findById(id).orElseThrow();
        assertThat(after1.getAttempts()).isEqualTo(1);
        assertThat(after1.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(after1.getNextAttemptAt()).isAfter(OffsetDateTime.now());

        after1.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        jobs.save(after1);
        worker.poll();

        Job after2 = jobs.findById(id).orElseThrow();
        after2.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        jobs.save(after2);
        worker.poll();

        Job finalState = jobs.findById(id).orElseThrow();
        assertThat(finalState.getAttempts()).isEqualTo(3);
        assertThat(finalState.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(finalState.getErrorMessage()).contains("intentional failure");
    }

    @Test
    @DisplayName("an unknown job type is recorded as an error, not lost")
    void unknownTypeIsDeadLettered() {
        UUID id = newJob("no-such-executor").getId();
        worker.poll();
        Job j = jobs.findById(id).orElseThrow();
        assertThat(j.getAttempts()).isEqualTo(1);
        assertThat(j.getErrorMessage()).contains("No executor registered");
    }

    @Test
    @DisplayName("concurrent workers never execute the same job twice")
    void concurrentWorkersDoNotDoubleProcess() throws Exception {
        int jobCount = 30;
        for (int i = 0; i < jobCount; i++) {
            newJob("echo");
        }

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    worker.poll();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        List<Job> all = jobs.findAll();
        assertThat(all).hasSize(jobCount);
        assertThat(all).allSatisfy(j -> {
            assertThat(j.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
            assertThat(j.getAttempts()).isZero();
        });
    }
}