package com.bluecollar.common.config;

import com.bluecollar.common.security.RateLimitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(prefix = "bluecollar.security.rate-limit", name = "enabled", havingValue = "true")
    public RateLimitService rateLimitService(
            @Value("${bluecollar.security.rate-limit.max-requests:20}") int maxRequests,
            @Value("${bluecollar.security.rate-limit.window-ms:60000}") long windowMs
    ) {
        return new RateLimitService(maxRequests, windowMs);
    }
}
