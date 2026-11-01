package com.expenseanalyzer.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class RateLimiterTest {

    private final UUID userId = UUID.randomUUID();
    private StringRedisTemplate redis;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        limiter = new RateLimiter(redis);
    }

    private void count(long value) {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(value);
    }

    @Test
    void allowsUpToFiveAttemptsThenAnswers429() {
        for (long n = 1; n <= 5; n++) {
            count(n);
            assertThatCode(() -> limiter.check("pwd", userId)).doesNotThrowAnyException();
        }
        count(6);
        assertThatThrownBy(() -> limiter.check("pwd", userId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
    }

    @Test
    void keyUsesPurposeAndUserIdAndWindowIs300Seconds() {
        count(1);
        limiter.check("pwd", userId);
        verify(redis).execute(any(RedisScript.class), eq(List.of("rate:pwd:" + userId)), eq("300"));
    }

    @Test
    void redisFailureFailsClosedWith503() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("down"));
        assertThatThrownBy(() -> limiter.check("pwd", userId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void resetDeletesTheKey() {
        limiter.reset("pwd", userId);
        verify(redis).delete("rate:pwd:" + userId);
    }
}
