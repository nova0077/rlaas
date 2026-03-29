package com.ratelimiter.repository;

import com.ratelimiter.model.entity.RateLimitPolicy;
import com.ratelimiter.model.enums.KeyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PolicyRepository — The Data Access Layer (DAL)
 */
@Repository
public interface PolicyRepository extends JpaRepository<RateLimitPolicy, Long> {

    /**
     * Find all active policies for a specific dimension (USER, IP, or API).
     *
     * @param keyType The dimension to search for (USER, IP, API)
     * @return List of active policies for that dimension
     */
    List<RateLimitPolicy> findByKeyTypeAndEnabledTrue(KeyType keyType);
}
