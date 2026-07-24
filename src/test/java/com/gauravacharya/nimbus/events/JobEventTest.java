package com.gauravacharya.nimbus.events;

import com.gauravacharya.nimbus.job.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cancellation and event-publishing behaviour.
 *
 * Event publishing is disabled here: the publisher is fire-and-forget by design, so
 * these tests assert the state transitions that drive events rather than the delivery
 * itself, and avoid requiring a broker in the unit test path.
 */
@SpringBootTest(properties = {
        "nimbus.worker.scheduled=false",
        "nimbus.events.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class JobEventTest {

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
                                {"email":"events@test.dev","password":"password123","firstName":"E","lastName":"V"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = mapper.readTree(json).get("token").asText();
    }

    private String submit(String body) throws Exception {
        return mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("a pending job can be cancelled")
    void pendingJobCanBeCancelled() throws Exception {
        String json = submit("{\"type\":\"echo\",\"scheduledAt\":\"" + OffsetDateTime.now().plusHours(1) + "\"}");
        String id = mapper.readTree(json).get("id").asText();

        mockMvc.perform(delete("/api/jobs/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(jobs.findAll().get(0).getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    @DisplayName("a cancelled job is never claimed by the worker")
    void cancelledJobIsNotClaimed() throws Exception {
        String json = submit("{\"type\":\"echo\"}");
        String id = mapper.readTree(json).get("id").asText();

        mockMvc.perform(delete("/api/jobs/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        worker.poll();

        Job job = jobs.findAll().get(0);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getStartedAt()).isNull();
    }

    @Test
    @DisplayName("cancelling a recurring job stops the schedule")
    void cancellingRecurringJobStopsSchedule() throws Exception {
        submit("{\"type\":\"echo\",\"cronExpression\":\"*/1 * * * * *\"}");
        Job original = jobs.findAll().get(0);

        // let it produce one successor
        original.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        jobs.save(original);
        worker.poll();
        long afterOneRun = jobs.count();
        assertThat(afterOneRun).isEqualTo(2);

        // cancel the pending successor
        Job pending = jobs.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.PENDING).findFirst().orElseThrow();
        mockMvc.perform(delete("/api/jobs/{id}", pending.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // further polls must not extend the series
        for (int i = 0; i < 3; i++) worker.poll();
        assertThat(jobs.count()).isEqualTo(afterOneRun);
    }

    @Test
    @DisplayName("cancelling a running job is rejected with 409")
    void runningJobCannotBeCancelled() throws Exception {
        String json = submit("{\"type\":\"echo\"}");
        String id = mapper.readTree(json).get("id").asText();

        Job job = jobs.findAll().get(0);
        job.setStatus(JobStatus.RUNNING);
        jobs.save(job);

        mockMvc.perform(delete("/api/jobs/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a user cannot cancel another user's job")
    void cannotCancelAnotherUsersJob() throws Exception {
        String json = submit("{\"type\":\"echo\"}");
        String id = mapper.readTree(json).get("id").asText();

        String otherJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"other@test.dev","password":"password123","firstName":"O","lastName":"T"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String otherToken = mapper.readTree(otherJson).get("token").asText();

        mockMvc.perform(delete("/api/jobs/{id}", id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }
}