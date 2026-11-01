package com.expenseanalyzer.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RateLimiter fails closed, so a script or serialisation fault would block every password action: run it for real. */
@Testcontainers
class RateLimiterRedisTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    private StringRedisTemplate template() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    @Test
    void loginIsLimitedPerEmailAndAddressTogether() {
        RateLimiter limiter = new RateLimiter(template());
        String email = UUID.randomUUID() + "@example.com";

        for (int i = 0; i < RateLimiter.MAX_ATTEMPTS; i++) {
            limiter.checkLogin(email, "10.0.0.1");
        }
        assertThatThrownBy(() -> limiter.checkLogin(email.toUpperCase(), "10.0.0.1"))
                .isInstanceOf(ResponseStatusException.class);

        limiter.checkLogin(email, "10.0.0.2"); // the owner elsewhere is not locked out
        limiter.checkLogin("other-" + email, "10.0.0.1");

        limiter.resetLogin(email, "10.0.0.1");
        limiter.checkLogin(email, "10.0.0.1");
    }

    @Test
    void loginFailsOpenWhenRedisIsDown() {
        LettuceConnectionFactory down = new LettuceConnectionFactory("localhost", 1);
        down.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(down);
        redis.afterPropertiesSet();

        new RateLimiter(redis).checkLogin("a@example.com", "10.0.0.1");
    }

    @Test
    void limitsAttemptsSetsExpiryAndResets() {
        StringRedisTemplate redis = template();
        RateLimiter limiter = new RateLimiter(redis);
        UUID user = UUID.randomUUID();

        for (int i = 0; i < RateLimiter.MAX_ATTEMPTS; i++) {
            limiter.check("test", user);
        }
        assertThatThrownBy(() -> limiter.check("test", user))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(redis.getExpire("rate:test:" + user)).isBetween(1L, (long) RateLimiter.WINDOW_SECONDS);

        limiter.reset("test", user);
        limiter.check("test", user);
    }
}
