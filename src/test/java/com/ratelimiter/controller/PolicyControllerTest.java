package com.ratelimiter.controller;

import com.ratelimiter.model.entity.RateLimitPolicy;
import com.ratelimiter.model.enums.AlgorithmType;
import com.ratelimiter.model.enums.KeyType;
import com.ratelimiter.service.PolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvcTest for PolicyController.
 * Loads ONLY the web layer (no full app context, no DB, no Redis).
 */
@WebMvcTest(PolicyController.class)
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PolicyService policyService;

    private RateLimitPolicy createSamplePolicy() {
        return RateLimitPolicy.builder()
                .id(1L)
                .name("test-policy")
                .keyType(KeyType.IP)
                .keyPattern("10.0.*")
                .algorithmType(AlgorithmType.FIXED_WINDOW)
                .maxRequests(100)
                .windowSizeMs(60000L)
                .enabled(true)
                .useRedis(false)
                .description("Test policy")
                .build();
    }

    // ── GET /api/v1/admin/policies ───────────────────────────────────────────

    @Test
    @DisplayName("GET /policies should return all policies")
    void getAllPoliciesShouldReturnList() throws Exception {
        when(policyService.getAllPolicies()).thenReturn(List.of(createSamplePolicy()));

        mockMvc.perform(get("/api/v1/admin/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("test-policy"));
    }

    // ── GET /api/v1/admin/policies/{id} ──────────────────────────────────────

    @Test
    @DisplayName("GET /policies/{id} should return policy when found")
    void getPolicyShouldReturnWhenFound() throws Exception {
        when(policyService.getPolicyById(1L)).thenReturn(Optional.of(createSamplePolicy()));

        mockMvc.perform(get("/api/v1/admin/policies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("test-policy"))
                .andExpect(jsonPath("$.data.algorithmType").value("FIXED_WINDOW"));
    }

    @Test
    @DisplayName("GET /policies/{id} should return 404 when not found")
    void getPolicyShouldReturn404WhenNotFound() throws Exception {
        when(policyService.getPolicyById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/admin/policies/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Policy not found"));
    }

    // ── POST /api/v1/admin/policies ──────────────────────────────────────────

    @Test
    @DisplayName("POST /policies should create a new policy")
    void createPolicyShouldReturnCreated() throws Exception {
        RateLimitPolicy policy = createSamplePolicy();
        when(policyService.createPolicy(any(RateLimitPolicy.class))).thenReturn(policy);

        String json = objectMapper.writeValueAsString(policy);

        mockMvc.perform(post("/api/v1/admin/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("test-policy"));
    }

    @Test
    @DisplayName("POST /policies should return 400 for invalid policy (missing name)")
    void createPolicyShouldReturn400ForInvalidBody() throws Exception {
        // Missing required fields
        String invalidJson = """
                {
                    "keyType": "IP",
                    "keyPattern": "*",
                    "algorithmType": "FIXED_WINDOW",
                    "maxRequests": 100,
                    "windowSizeMs": 60000
                }
                """;

        mockMvc.perform(post("/api/v1/admin/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/v1/admin/policies/{id} ──────────────────────────────────────

    @Test
    @DisplayName("PUT /policies/{id} should update existing policy")
    void updatePolicyShouldReturnUpdated() throws Exception {
        RateLimitPolicy existing = createSamplePolicy();
        RateLimitPolicy updated = createSamplePolicy();
        updated.setMaxRequests(200);

        when(policyService.getPolicyById(1L)).thenReturn(Optional.of(existing));
        when(policyService.updatePolicy(any(RateLimitPolicy.class))).thenReturn(updated);

        String json = objectMapper.writeValueAsString(updated);

        mockMvc.perform(put("/api/v1/admin/policies/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maxRequests").value(200));
    }

    @Test
    @DisplayName("PUT /policies/{id} should return 404 when not found")
    void updatePolicyShouldReturn404WhenNotFound() throws Exception {
        when(policyService.getPolicyById(999L)).thenReturn(Optional.empty());

        RateLimitPolicy policy = createSamplePolicy();
        String json = objectMapper.writeValueAsString(policy);

        mockMvc.perform(put("/api/v1/admin/policies/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/v1/admin/policies/{id} ───────────────────────────────────

    @Test
    @DisplayName("DELETE /policies/{id} should delete and return success")
    void deletePolicyShouldReturnSuccess() throws Exception {
        when(policyService.getPolicyById(1L)).thenReturn(Optional.of(createSamplePolicy()));

        mockMvc.perform(delete("/api/v1/admin/policies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsString("deleted")));
    }

    @Test
    @DisplayName("DELETE /policies/{id} should return 404 when not found")
    void deletePolicyShouldReturn404WhenNotFound() throws Exception {
        when(policyService.getPolicyById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/admin/policies/999"))
                .andExpect(status().isNotFound());
    }
}
