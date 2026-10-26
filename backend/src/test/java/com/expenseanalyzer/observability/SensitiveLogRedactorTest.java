package com.expenseanalyzer.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveLogRedactorTest {

    @Test
    void redactsPasswordAssignment() {
        assertThat(SensitiveLogRedactor.redact("Request password: hunter2"))
                .doesNotContain("hunter2")
                .contains("[REDACTED]");
    }

    @Test
    void redactsBearerAndJwt() {
        assertThat(SensitiveLogRedactor.redact("Authorization: Bearer abc.def.ghi"))
                .doesNotContain("abc.def.ghi");
        assertThat(SensitiveLogRedactor.redact("got eyJhbGciOi.eyJzdWIi.sig here"))
                .doesNotContain("eyJhbGciOi")
                .doesNotContain("eyJzdWIi");
    }

    @Test
    void redactsSecretsAndApiKeys() {
        assertThat(SensitiveLogRedactor.redact("secret=abc")).doesNotContain("abc");
        assertThat(SensitiveLogRedactor.redact("api_key: xyz")).doesNotContain("xyz");
        assertThat(SensitiveLogRedactor.redact("refresh_token=r1")).doesNotContain("r1");
    }

    @Test
    void redactsDescriptionAndStatementContent() {
        assertThat(SensitiveLogRedactor.redact("description=COFFEE SHOP 4.50"))
                .doesNotContain("COFFEE")
                .doesNotContain("4.50");
        assertThat(SensitiveLogRedactor.redact("statement: 2024-01-01,Rent,-1200"))
                .doesNotContain("Rent")
                .doesNotContain("1200");
    }

    @Test
    void leavesBenignMessagesUntouched() {
        String msg = "Registration failed: Email already registered";
        assertThat(SensitiveLogRedactor.redact(msg)).isEqualTo(msg);
        assertThat(SensitiveLogRedactor.redact(null)).isNull();
    }
}
