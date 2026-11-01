package com.expenseanalyzer.auth.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Fixed-window attempt limiter backed by Redis: at most {@value #MAX_ATTEMPTS} attempts per
 * {@value #WINDOW_SECONDS} seconds per purpose and user. Every call to {@link #check} counts as an attempt.
 * Fails closed: if Redis is unreachable the caller gets 503, never a free pass.
 */
@Component
public class RateLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final int WINDOW_SECONDS = 300;

    // INCR and the first-increment EXPIRE run as one atomic script, so a key can never be left without a TTL.
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c", Long.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Counts one attempt; throws 429 above the limit, 503 if Redis fails. */
    public void check(String purpose, UUID userId) {
        Long count;
        try {
            count = redis.execute(INCREMENT, List.of(key(purpose, userId)), String.valueOf(WINDOW_SECONDS));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");
        }
        if (count == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");
        }
        if (count > MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
        }
    }

    /** Clears the counter after a success. A Redis failure here is ignored: the key expires on its own. */
    public void reset(String purpose, UUID userId) {
        try {
            redis.delete(key(purpose, userId));
        } catch (RuntimeException ignored) {
            // the window expires by itself
        }
    }

    private static String key(String purpose, UUID userId) {
        return "rate:" + purpose + ":" + userId;
    }
}
