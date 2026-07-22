package com.gauravacharya.nimbus.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gauravacharya.nimbus.support.PostgresTestContainer;
import com.gauravacharya.nimbus.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "nimbus.worker.scheduled=false")
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class JobApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JobRepository jobRepository;
    @Autowired UserRepository userRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jobRepository.deleteAll();
        userRepository.deleteAll();

        String body = """
                {"email":"jobs-test@example.com","password":"password123","firstName":"F","lastName":"L"}
                """;
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = mapper.readTree(json).get("token").asText();
    }

    private String bearer() { return "Bearer " + token; }

    @Test
    @DisplayName("POST /api/jobs returns 201 with Location header and persists a PENDING job")
    void submitJob_returns201AndPersists() throws Exception {
        String body = """
                {"type":"send-email","payload":"{\\"to\\":\\"a@b.com\\"}"}
                """;

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.type").value("send-email"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.maxAttempts").value(3));

        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/jobs without type returns 400 problem+json")
    void submitJob_missingType_returns400() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"no type\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("type")));

        assertThat(jobRepository.count()).isZero();
    }

    @Test
    @DisplayName("GET /api/jobs/{id} returns 404 for unknown id")
    void getJob_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", UUID.randomUUID())
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("GET /api/jobs returns a paginated payload")
    void listJobs_isPaginated() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/jobs")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"job-" + i + "\"}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/jobs").param("size", "2")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }
}