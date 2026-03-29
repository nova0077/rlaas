package com.ratelimiter.service;

import com.ratelimiter.model.entity.RateLimitPolicy;
import com.ratelimiter.model.enums.KeyType;
import com.ratelimiter.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * PolicyService — Business Logic for Rate Limit Policies
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;

    public List<RateLimitPolicy> getAllPolicies() {
        return policyRepository.findAll();
    }

    public Optional<RateLimitPolicy> getPolicyById(@NonNull Long id) {
        return policyRepository.findById(id);
    }

    /**
     * Gets all active policies for a specific metric (USER, IP, API).
     */
    @Cacheable(value = "activePolicies", key = "#keyType.name()")
    public List<RateLimitPolicy> getActivePolicies(KeyType keyType) {
        log.debug("DB HIT: Fetching active {} policies from DB", keyType);
        return policyRepository.findByKeyTypeAndEnabledTrue(keyType);
    }

    /**
     * CORE ROUTING LOGIC: Finds the first policy that matches the incoming key.
     */
    public Optional<RateLimitPolicy> findMatchingPolicy(KeyType keyType, String key) {
        if (key == null)
            return Optional.empty();

        return getActivePolicies(keyType).stream()
                .filter(policy -> policy.matches(key))
                .findFirst();
    }

    @Transactional
    @CacheEvict(value = "activePolicies", key = "#policy.keyType.name()")
    public RateLimitPolicy createPolicy(RateLimitPolicy policy) {
        log.info("Creating new policy: {}", policy.getName());
        return policyRepository.save(policy);
    }

    @Transactional
    @CacheEvict(value = "activePolicies", key = "#policy.keyType.name()")
    public RateLimitPolicy updatePolicy(RateLimitPolicy policy) {
        log.info("Updating policy: {}", policy.getName());
        return policyRepository.save(policy);
    }

    @Transactional
    @CacheEvict(value = "activePolicies", allEntries = true)
    public void deletePolicy(@NonNull Long id) {
        log.info("Deleting policy id: {}", id);
        policyRepository.deleteById(id);
    }
}
