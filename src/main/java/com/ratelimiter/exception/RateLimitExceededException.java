package com.ratelimiter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * RateLimitExceededException
 * 
 * Custom RuntimeException thrown when a rate limit is exceeded.
 * 
 * By using @ResponseStatus, Spring MVC knows to automatically map this
 * exception to an HTTP 429 status code if we don't catch it ourselves.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
