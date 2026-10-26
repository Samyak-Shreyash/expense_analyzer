package com.expenseanalyzer.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/** Masks secrets and raw transaction/statement content in structured log {@code message} and {@code stack_trace}. */
public class SensitiveLogRedactor implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    private static final String MASK = "[REDACTED]";
    private static final int CI = Pattern.CASE_INSENSITIVE;

    private static final Pattern SECRET = Pattern.compile(
            "(password|passwd|pwd|secret|token|authorization|api[_-]?key|refresh[_-]?token|access[_-]?token)(\\s*[=:]\\s*)\\S+",
            CI);
    private static final Pattern BEARER = Pattern.compile("Bearer\\s+[A-Za-z0-9._~+/=-]+", CI);
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern CONTENT = Pattern.compile("(description|statement)(\\s*[=:]\\s*).+", CI);

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        // Bearer/JWT first so "Authorization: Bearer x" keeps its scheme marker before the generic key rule.
        String out = BEARER.matcher(text).replaceAll("Bearer " + MASK);
        out = JWT.matcher(out).replaceAll(MASK);
        out = SECRET.matcher(out).replaceAll("$1$2" + MASK);
        return CONTENT.matcher(out).replaceAll("$1$2" + MASK);
    }

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        for (String name : new String[] {"message", "stack_trace"}) {
            members.applyingValueProcessor(
                    JsonWriter.ValueProcessor.of(String.class, SensitiveLogRedactor::redact).whenHasPath(name));
        }
    }
}
