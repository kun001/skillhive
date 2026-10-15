package com.iflytek.skillhub.service.knowledge;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Markdown extraction for knowledge files; on by default because it never leaves the server. */
@ConfigurationProperties(prefix = "skillhub.knowledge.text")
public record KnowledgeTextProperties(boolean enabled, long maxFileBytes, int maxChars, int maxAttempts,
                                      Duration retryDelay, long pollIntervalMs) {
    public KnowledgeTextProperties {
        if (maxFileBytes <= 0 || maxChars < 1000 || maxAttempts < 1 || maxAttempts > 5
                || retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("Invalid knowledge text configuration");
        }
    }
}
