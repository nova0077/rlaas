package com.ratelimiter.controller;

import com.ratelimiter.model.dto.ApiResponse;
import com.ratelimiter.model.entity.RateLimitPolicy;
import com.ratelimiter.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.lang.NonNull;

import java.util.List;

/**
 * PolicyController — Admin API for Managing Rate Limit Policies
 */
@RestController
@RequestMapping("/api/v1/admin/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    /**
     * GET /api/v1/admin/policies
     * Retrieves all policies.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RateLimitPolicy>>> getAllPolicies() {
        List<RateLimitPolicy> policies = policyService.getAllPolicies();
        return ResponseEntity.ok(ApiResponse.success("Policies retrieved", policies));
    }

    /**
     * GET /api/v1/admin/policies/{id}
     * Retrieve a single policy by its ID.
     * 
     * @PathVariable extracts the {id} from the URL path.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RateLimitPolicy>> getPolicy(@PathVariable @NonNull Long id) {
        return policyService.getPolicyById(id)
                .map(policy -> ResponseEntity.ok(ApiResponse.success("Policy found", policy)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Policy not found")));
    }

    /**
     * POST /api/v1/admin/policies
     * Create a new policy.
     * 
     * @Valid triggers Bean Validation constraints (@NotNull, @Min) defined on the
     *        Entity.
     *        If validation fails, Spring throws MethodArgumentNotValidException
     *        BEFORE this code runs.
     * 
     * @RequestBody parses the incoming JSON into the RateLimitPolicy Java object.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RateLimitPolicy>> createPolicy(
            @Valid @RequestBody RateLimitPolicy policy) {

        // Prevent clients from forcing an ID on creation
        policy.setId(null);

        try {
            RateLimitPolicy saved = policyService.createPolicy(policy);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Policy created successfully", saved));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Failed to create policy: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/v1/admin/policies/{id}
     * Update an entire existing policy.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RateLimitPolicy>> updatePolicy(
            @PathVariable @NonNull Long id,
            @Valid @RequestBody RateLimitPolicy policyDetails) {

        return policyService.getPolicyById(id).map(existing -> {
            // Note: We can use Mapper Library here, it is fast, typesafe & avoids
            // reflection
            existing.setName(policyDetails.getName());
            existing.setKeyType(policyDetails.getKeyType());
            existing.setKeyPattern(policyDetails.getKeyPattern());
            existing.setAlgorithmType(policyDetails.getAlgorithmType());
            existing.setMaxRequests(policyDetails.getMaxRequests());
            existing.setWindowSizeMs(policyDetails.getWindowSizeMs());
            existing.setRefillRate(policyDetails.getRefillRate());
            existing.setBucketSize(policyDetails.getBucketSize());
            existing.setUseRedis(policyDetails.getUseRedis());
            existing.setEnabled(policyDetails.getEnabled());
            existing.setDescription(policyDetails.getDescription());

            RateLimitPolicy saved = policyService.updatePolicy(existing);
            return ResponseEntity.ok(ApiResponse.success("Policy updated", saved));

        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Policy not found")));
    }

    /**
     * DELETE /api/v1/admin/policies/{id}
     * Delete a policy.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable @NonNull Long id) {
        if (!policyService.getPolicyById(id).isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Policy not found"));
        }

        policyService.deletePolicy(id);
        return ResponseEntity.ok(ApiResponse.success("Policy deleted successfully"));
    }
}