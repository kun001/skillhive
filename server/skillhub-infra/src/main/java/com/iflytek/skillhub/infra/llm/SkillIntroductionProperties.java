package com.iflytek.skillhub.infra.llm;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skillhub.introduction")
public record SkillIntroductionProperties(boolean enabled, String baseUrl, String apiKey, String model,
        Duration connectTimeout, Duration readTimeout, double temperature, int maxTokens,
        int maxInputChars, int maxAttempts, Duration retryDelay, String responseFormat) {
    public SkillIntroductionProperties {
        if (enabled) {
            URI uri = URI.create(baseUrl);
            if (!uri.isAbsolute() || uri.getHost() == null || !"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || model == null || model.isBlank() || maxTokens < 256 || maxTokens > 8192
                    || maxInputChars < 500 || maxInputChars > 100000 || maxAttempts < 1 || maxAttempts > 5
                    || !Double.isFinite(temperature) || temperature < 0 || temperature > 2
                    || !validTimeout(connectTimeout, 60) || !validTimeout(readTimeout, 600)
                    || !validTimeout(retryDelay, 86400)
                    || !java.util.Set.of("json_schema", "json_object", "none").contains(responseFormat)) {
                throw new IllegalArgumentException("Invalid skill introduction configuration");
            }
        }
    }

    private static boolean validTimeout(Duration value, int maxSeconds) {
        return value != null && !value.isNegative() && !value.isZero() && value.compareTo(Duration.ofSeconds(maxSeconds)) <= 0;
    }

    @Override
    public String toString() { return "SkillIntroductionProperties[enabled=" + enabled + ", credentials=REDACTED]"; }
}
