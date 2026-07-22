package com.gauravacharya.nimbus.job;

import com.gauravacharya.nimbus.support.PostgresTestContainer;
import com.gauravacharya.nimbus.user.UserRepository;
import com.gauravacharya.nimbus.worker.JobWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "nimbus.worker.scheduled=false")
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class JobSchedulingTest {

    @Autowired MockMvc mockMvc;
    @Autowired JobRepository jobs;
    @Autowired UserRepository users;
    @Autowired JobWorker worker;

    private final ObjectMapper mapper = new ObjectMapper();
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jobs.deleteAll();
        users.deleteAll();
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"sched@test.dev","password":"password123","firstName":"S","lastName":"T"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = mapper.readTree(json).get("token").asText();
    }

    private String submit(String body) throws Exception {
        return mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("a job scheduled in the future is not claimed until it is due")
    void futureJobIsNotClaimedEarly() throws Exception {
        OffsetDateTime future = OffsetDateTime.now().plusHours(1);
        submit("{\"type\":\"echo\",\"scheduledAt\":\"" + future + "\"}");

        worker.poll();

        Job job = jobs.findAll().get(0);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getNextAttemptAt()).isAfter(OffsetDateTime.now().plusMinutes(50));
    }

    @Test
    @DisplayName("a job with no schedule runs on the next poll")
    void immediateJobRunsRightAway() throws Exception {
        submit("{\"type\":\"echo\",\"payload\":\"now\"}");

        worker.poll();

        assertThat(jobs.findAll().get(0).getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("an invalid cron expression is rejected at submission")
    void invalidCronIsRejected() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"echo\",\"cronExpression\":\"not a cron\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        assertThat(jobs.count()).isZero();
    }

    @Test
    @DisplayName("a recurring job enqueues its next occurrence after a successful run")
    void recurringJobSchedulesNextOccurrence() throws Exception {
        submit("{\"type\":\"echo\",\"payload\":\"tick\",\"cronExpression\":\"*/1 * * * * *\"}");

        Job first = jobs.findAll().get(0);
        // make it due now rather than waiting on the cron boundary
        first.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        jobs.save(first);

        worker.poll();

        List<Job> all = jobs.findAll();
        assertThat(all).hasSize(2);

        Job completed = all.stream().filter(j -> j.getId().equals(first.getId())).findFirst().orElseThrow();
        Job next = all.stream().filter(j -> !j.getId().equals(first.getId())).findFirst().orElseThrow();

        assertThat(completed.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(next.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(next.getCronExpression()).isEqualTo("*/1 * * * * *");
        assertThat(next.getParentJobId()).isEqualTo(first.getId());
        assertThat(next.getNextAttemptAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    @DisplayName("every occurrence links back to the original job, not the previous one")
    void occurrencesShareTheSameParent() throws Exception {
        submit("{\"type\":\"echo\",\"payload\":\"tick\",\"cronExpression\":\"*/1 * * * * *\"}");
        Job original = jobs.findAll().get(0);

        for (int i = 0; i < 3; i++) {
            jobs.findAll().stream()
                    .filter(j -> j.getStatus() == JobStatus.PENDING)
                    .forEach(j -> {
                        j.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
                        jobs.save(j);
                    });
            worker.poll();
        }

        List<Job> occurrences = jobs.findAll().stream()
                .filter(j -> !j.getId().equals(original.getId()))
                .toList();

        assertThat(occurrences).isNotEmpty();
        assertThat(occurrences).allSatisfy(j ->
                assertThat(j.getParentJobId()).isEqualTo(original.getId()));
    }

    @Test
    @DisplayName("a failed recurring job does not enqueue a next occurrence")
    void failedRecurringJobDoesNotReschedule() throws Exception {
        submit("{\"type\":\"always-fails\",\"cronExpression\":\"*/1 * * * * *\"}");

        Job job = jobs.findAll().get(0);
        job.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        jobs.save(job);

        worker.poll();

        // retries first; the successor is only created on success
        assertThat(jobs.count()).isEqualTo(1);
        assertThat(jobs.findAll().get(0).getAttempts()).isEqualTo(1);
    }
}