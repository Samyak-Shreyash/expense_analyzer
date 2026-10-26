package com.expenseanalyzer.observability;

import com.expenseanalyzer.auth.dto.RegisterRequest;
import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class LogRedactionIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger("test");

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Test
    void loggedSecretsAreRedactedInJsonOutputAndCorrelationIdSurvives(CapturedOutput output) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationIdFilter.MDC_KEY, "cid-12345")) {
            log.warn("login password=hunter2 token=abc123");
        }

        assertThat(output).contains("[REDACTED]").doesNotContain("hunter2").doesNotContain("abc123");
        assertThat(output).contains("cid-12345");
    }

    @Test
    void bearerTokenInThrowableIsRedacted(CapturedOutput output) {
        log.error("failure", new IllegalStateException("Bearer abc.def.ghi"));

        assertThat(output).contains("[REDACTED]").doesNotContain("abc.def.ghi");
    }

    @Test
    void registrationFailuresDoNotLogPasswordOrEmail(CapturedOutput output) {
        String email = "pii-check@example.com";
        assertThatThrownBy(() -> authService.register(new RegisterRequest(email, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authService.register(new RegisterRequest(email, "Sh0rt!")))
                .isInstanceOf(IllegalArgumentException.class);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User(email, "x")));
        assertThatThrownBy(() -> authService.register(new RegisterRequest(email, "Str0ngPassw0rd!")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(output).contains("Registration failed").doesNotContain("Sh0rt!")
                .doesNotContain(email);
    }
}
