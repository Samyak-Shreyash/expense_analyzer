package com.expenseanalyzer.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fixed-window attempt limiter backed by Redis: at most {@value #MAX_ATTEMPTS} attempts per
 * {@value #WINDOW_SECONDS} seconds per purpose and user. Every call to {@link #check} counts as an attempt.
 * Fails closed: if Redis is unreachable the caller gets 503, never a free pass.
 */
@Slf4j
@Component
public class RateLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final int WINDOW_SECONDS = 300;
    static final int MAX_REFRESH_ATTEMPTS = 60;
    static final int MAX_REGISTER_ATTEMPTS = 10;
    static final int MAX_UPLOADS = 30;
    // Across all addresses. High enough that an attacker cannot easily lock the owner out for long.
    static final int MAX_ACCOUNT_LOGIN_ATTEMPTS = 20;

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

    /**
     * Counts one login attempt for this email from this address; throws 429 above the limit. Fails open: login must
     * keep working when Redis is down, unlike the password endpoints.
     */
    public void checkLogin(String email, String ip) {
        countFailingOpen(loginKey(email, ip), MAX_ATTEMPTS);
        countFailingOpen(accountLoginKey(email), MAX_ACCOUNT_LOGIN_ATTEMPTS);
    }

    /** Counts one registration from this address; throws 429 above the limit. Fails open, like login. */
    public void checkRegister(String ip) {
        countFailingOpen("rate:register:" + sha256(ip), MAX_REGISTER_ATTEMPTS);
    }

    /** Counts one statement upload by this user; throws 429 above the limit. Fails open, like login. */
    public void checkUpload(UUID userId) {
        countFailingOpen(key("upload", userId), MAX_UPLOADS);
    }

    /**
     * Counts one token refresh from this address; throws 429 above the limit. Higher than login because tokens cannot
     * be guessed and many users can share an address. Fails open, like login.
     */
    public void checkRefresh(String ip) {
        countFailingOpen("rate:refresh:" + sha256(ip), MAX_REFRESH_ATTEMPTS);
    }

    private void countFailingOpen(String key, int limit) {
        Long count;
        try {
            count = redis.execute(INCREMENT, List.of(key), String.valueOf(WINDOW_SECONDS));
        } catch (RuntimeException e) {
            log.warn("Rate limit skipped: error={}", e.getClass().getSimpleName());
            return;
        }
        if (count != null && count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
        }
    }

    /** Clears the login counter after a success. */
    public void resetLogin(String email, String ip) {
        try {
            redis.delete(loginKey(email, ip));
            redis.delete(accountLoginKey(email));
        } catch (RuntimeException ignored) {
            // the window expires by itself
        }
    }

    // Hashed so Redis never holds an email or address.
    private static String loginKey(String email, String ip) {
        return "rate:login:" + sha256(email.toLowerCase(Locale.ROOT) + "|" + ip);
    }

    private static String accountLoginKey(String email) {
        return "rate:login-account:" + sha256(email.toLowerCase(Locale.ROOT));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every JVM ships SHA-256
        }
    }

    private static String key(String purpose, UUID userId) {
        return "rate:" + purpose + ":" + userId;
    }
}
