package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisAuthSessionEpochStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisAuthSessionEpochStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisAuthSessionEpochStore(redisTemplate, "skillhub:session");
    }

    @Test
    void currentEpoch_returnsZeroWhenMissing() {
        when(valueOperations.get("skillhub:session:epochs:usr_1")).thenReturn(null);
        assertThat(store.currentEpoch("usr_1")).isZero();
    }

    @Test
    void bumpEpoch_incrementsRedisKey() {
        when(valueOperations.increment("skillhub:session:epochs:usr_1")).thenReturn(2L);
        assertThat(store.bumpEpoch("usr_1")).isEqualTo(2L);
        verify(valueOperations).increment("skillhub:session:epochs:usr_1");
    }
}
