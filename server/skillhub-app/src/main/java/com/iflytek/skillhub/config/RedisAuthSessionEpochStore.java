package com.iflytek.skillhub.config;

import com.iflytek.skillhub.auth.session.AuthSessionEpochStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Redis-backed {@link AuthSessionEpochStore} living alongside Spring Session keys.
 */
@Component
public class RedisAuthSessionEpochStore implements AuthSessionEpochStore {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisAuthSessionEpochStore(
            StringRedisTemplate redisTemplate,
            @Value("${spring.session.redis.namespace:spring:session}") String namespace) {
        this.redisTemplate = redisTemplate;
        String normalized = namespace.endsWith(":") ? namespace : namespace + ":";
        this.keyPrefix = normalized + "epochs:";
    }

    @Override
    public long currentEpoch(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0L;
        }
        String value = redisTemplate.opsForValue().get(key(userId));
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    @Override
    public long bumpEpoch(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0L;
        }
        Long next = redisTemplate.opsForValue().increment(key(userId));
        return next == null ? 0L : next;
    }

    private String key(String userId) {
        return keyPrefix + userId;
    }
}
