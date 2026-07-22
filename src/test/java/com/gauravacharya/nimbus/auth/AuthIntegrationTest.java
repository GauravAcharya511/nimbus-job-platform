package com.gauravacharya.nimbus.auth;

import com.gauravacharya.nimbus.support.PostgresTestContainer;
import com.gauravacharya.nimbus.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "nimbus.worker.scheduled=false")
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired com.gauravacharya.nimbus.job.JobRepository jobs;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void clean() { jobs.deleteAll(); users.deleteAll(); }

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"password123","firstName":"F","lastName":"L"}
                """.formatted(email);
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("token").asText();
    }

    private void submitJob(String token, String type) throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("unauthenticated requests to protected routes return 401")
    void protectedRouteRequiresToken() throws Exception {
        mockMvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login with wrong password returns 401")
    void wrongPasswordRejected() throws Exception {
        register("user@example.com");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("duplicate email registration is rejected")
    void duplicateEmailRejected() throws Exception {
        register("dupe@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dupe@example.com\",\"password\":\"password123\",\"firstName\":\"F\",\"lastName\":\"L\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a user sees only their own jobs")
    void jobsAreIsolatedPerUser() throws Exception {
        String alice = register("alice@example.com");
        String bob = register("bob@example.com");
        submitJob(alice, "alice-job");
        submitJob(bob, "bob-job");

        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("alice-job"));

        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("bob-job"));
    }
}