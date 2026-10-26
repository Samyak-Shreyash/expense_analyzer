package com.expenseanalyzer.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class NoHardcodedSecretsTest {

    private static final Pattern SENSITIVE_KEY = Pattern.compile("(?i).*(password|secret|key|token)[ 	]*:.*");
    private static final Pattern NON_EMPTY_DEFAULT = Pattern.compile(":[ 	]*[$][{][^}:]+:[^}]+}$");

    @Test
    void applicationYmlHasNoDefaultsForSensitiveKeys() throws IOException {
        List<String> offenders = Files.readAllLines(Path.of("src/main/resources/application.yml")).stream()
                .filter(l -> SENSITIVE_KEY.matcher(l.trim()).matches())
                .filter(l -> NON_EMPTY_DEFAULT.matcher(l).find())
                .toList();
        assertThat(offenders).isEmpty();
    }

    @Test
    void javaSourcesHaveNoDefaultJwtSecret() throws IOException {
        for (String f : List.of(
                "src/main/java/com/expenseanalyzer/auth/dto/JwtTokenProvider.java",
                "src/main/java/com/expenseanalyzer/auth/config/SecurityConfiguration.java")) {
            assertThat(Files.readString(Path.of(f))).doesNotContain("default-secret-key");
        }
    }
}
