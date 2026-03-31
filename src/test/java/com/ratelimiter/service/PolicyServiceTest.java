package com.ratelimiter.service;

import com.ratelimiter.model.entity.RateLimitPolicy;
import com.ratelimiter.model.enums.AlgorithmType;
import com.ratelimiter.model.enums.KeyType;
import com.ratelimiter.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PolicyService using Mockito.
 * No Spring context needed — pure unit test with mocked repository.
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private PolicyService policyService;

    private RateLimitPolicy samplePolicy;

    @BeforeEach
    void setUp() {
        samplePolicy = RateLimitPolicy.builder()
                .id(1L)
                .name("test-policy")
                .keyType(KeyType.IP)
                .keyPattern("192.168.*")
                .algorithmType(AlgorithmType.FIXED_WINDOW)
                .maxRequests(100)
                .windowSizeMs(60000L)
                .enabled(true)
                .useRedis(false)
                .build();
    }

    @Test
    @DisplayName("getAllPolicies should delegate to repository")
    void getAllPoliciesShouldDelegateToRepository() {
        when(policyRepository.findAll()).thenReturn(List.of(samplePolicy));

        List<RateLimitPolicy> result = policyService.getAllPolicies();

        assertEquals(1, result.size());
        assertEquals("test-policy", result.get(0).getName());
        verify(policyRepository).findAll();
    }

    @Test
    @DisplayName("getPolicyById should return policy when found")
    void getPolicyByIdShouldReturnWhenFound() {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(samplePolicy));

        Optional<RateLimitPolicy> result = policyService.getPolicyById(1L);

        assertTrue(result.isPresent());
        assertEquals("test-policy", result.get().getName());
    }

    @Test
    @DisplayName("getPolicyById should return empty when not found")
    void getPolicyByIdShouldReturnEmptyWhenNotFound() {
        when(policyRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<RateLimitPolicy> result = policyService.getPolicyById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("getActivePolicies should filter by keyType and enabled")
    void getActivePoliciesShouldFilterByKeyType() {
        when(policyRepository.findByKeyTypeAndEnabledTrue(KeyType.IP))
                .thenReturn(List.of(samplePolicy));

        List<RateLimitPolicy> result = policyService.getActivePolicies(KeyType.IP);

        assertEquals(1, result.size());
        verify(policyRepository).findByKeyTypeAndEnabledTrue(KeyType.IP);
    }

    @Test
    @DisplayName("findMatchingPolicy should return matching policy")
    void findMatchingPolicyShouldReturnMatchingPolicy() {
        when(policyRepository.findByKeyTypeAndEnabledTrue(KeyType.IP))
                .thenReturn(List.of(samplePolicy));

        Optional<RateLimitPolicy> result = policyService.findMatchingPolicy(KeyType.IP, "192.168.1.1");

        assertTrue(result.isPresent());
        assertEquals("test-policy", result.get().getName());
    }

    @Test
    @DisplayName("findMatchingPolicy should return empty when no match")
    void findMatchingPolicyShouldReturnEmptyWhenNoMatch() {
        when(policyRepository.findByKeyTypeAndEnabledTrue(KeyType.IP))
                .thenReturn(List.of(samplePolicy));

        Optional<RateLimitPolicy> result = policyService.findMatchingPolicy(KeyType.IP, "10.0.0.1");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findMatchingPolicy should return empty for null key")
    void findMatchingPolicyShouldReturnEmptyForNullKey() {
        Optional<RateLimitPolicy> result = policyService.findMatchingPolicy(KeyType.IP, null);

        assertFalse(result.isPresent());
        verifyNoInteractions(policyRepository);
    }

    @Test
    @DisplayName("createPolicy should save and return policy")
    void createPolicyShouldSaveAndReturn() {
        when(policyRepository.save(any(RateLimitPolicy.class))).thenReturn(samplePolicy);

        RateLimitPolicy result = policyService.createPolicy(samplePolicy);

        assertNotNull(result);
        assertEquals("test-policy", result.getName());
        verify(policyRepository).save(samplePolicy);
    }

    @Test
    @DisplayName("updatePolicy should save and return updated policy")
    void updatePolicyShouldSaveAndReturn() {
        samplePolicy.setMaxRequests(200);
        when(policyRepository.save(samplePolicy)).thenReturn(samplePolicy);

        RateLimitPolicy result = policyService.updatePolicy(samplePolicy);

        assertEquals(200, result.getMaxRequests());
        verify(policyRepository).save(samplePolicy);
    }

    @Test
    @DisplayName("deletePolicy should delegate to repository")
    void deletePolicyShouldDelegateToRepository() {
        doNothing().when(policyRepository).deleteById(1L);

        policyService.deletePolicy(1L);

        verify(policyRepository).deleteById(1L);
    }
}
