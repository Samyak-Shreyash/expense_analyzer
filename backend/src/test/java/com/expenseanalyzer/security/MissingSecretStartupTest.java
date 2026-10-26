package com.expenseanalyzer.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

class MissingSecretStartupTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(PropertySourcesPlaceholderConfigurer.class)
                    .withBean(JwtTokenProvider.class);

    @Test
    void startupFailsWhenJwtSecretMissing() {
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("app.oauth.jwt.secret");
        });
    }

    @Test
    void startsWhenJwtSecretProvided() {
        runner.withPropertyValues(
                        "app.oauth.jwt.secret=0123456789abcdef0123456789abcdef-dummy",
                        "app.oauth.jwt.tokenExpirationSeconds=3600")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
