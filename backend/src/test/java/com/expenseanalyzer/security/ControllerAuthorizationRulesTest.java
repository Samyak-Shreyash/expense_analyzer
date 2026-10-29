package com.expenseanalyzer.security;

import com.expenseanalyzer.auth.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard (SEC-006 / authorization matrix): every new controller must require authentication,
 * take the caller from the token and never accept a user id from the client.
 */
class ControllerAuthorizationRulesTest {

    /** Public by design: login, registration, token refresh. */
    private static final Set<Class<?>> PUBLIC_CONTROLLERS = Set.of(AuthController.class);

    /** Pre-existing exception: PUT /api/user/{userId}; UserService checks it against the token's user. */
    private static final Set<String> USER_ID_PARAM_ALLOWED = Set.of("UserController.updateUser");

    @Test
    void everyControllerFollowsTheRules() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            if (!PUBLIC_CONTROLLERS.contains(controller)) {
                violations.addAll(violations(controller));
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void scanFindsTheFinancialControllers() throws Exception {
        assertThat(controllers()).extracting(Class::getSimpleName)
                .contains("StatementController", "TransactionController", "AnalyticsController");
    }

    @Test
    void rulesCatchANonCompliantController() {
        assertThat(violations(LeakyController.class)).containsExactlyInAnyOrder(
                "LeakyController: missing class-level @PreAuthorize",
                "LeakyController.byUser: no @AuthenticationPrincipal Jwt parameter",
                "LeakyController.byUser: accepts userId from the client",
                "LeakyController.auditLogs: exposes audit logs");
    }

    private static List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> out = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.expenseanalyzer")) {
            out.add(Class.forName(bd.getBeanClassName()));
        }
        return out;
    }

    static List<String> violations(Class<?> controller) {
        List<String> out = new ArrayList<>();
        String name = controller.getSimpleName();
        if (!controller.isAnnotationPresent(PreAuthorize.class)) {
            out.add(name + ": missing class-level @PreAuthorize");
        }
        for (Method m : controller.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            String where = name + "." + m.getName();
            boolean hasPrincipal = Arrays.stream(m.getParameters())
                    .anyMatch(p -> p.isAnnotationPresent(AuthenticationPrincipal.class) && p.getType() == Jwt.class);
            if (!hasPrincipal) {
                out.add(where + ": no @AuthenticationPrincipal Jwt parameter");
            }
            if (!USER_ID_PARAM_ALLOWED.contains(where) && Arrays.stream(m.getParameters()).anyMatch(
                    ControllerAuthorizationRulesTest::isUserIdParameter)) {
                out.add(where + ": accepts userId from the client");
            }
            if (Arrays.stream(mapping.path()).anyMatch(p -> p.toLowerCase().contains("audit"))) {
                out.add(where + ": exposes audit logs");
            }
        }
        return out;
    }

    private static boolean isUserIdParameter(Parameter p) {
        RequestParam rp = p.getAnnotation(RequestParam.class);
        PathVariable pv = p.getAnnotation(PathVariable.class);
        String bound = rp != null ? rp.value() + rp.name() : pv != null ? pv.value() + pv.name() : null;
        if (bound == null) {
            return false;
        }
        return bound.equalsIgnoreCase("userId") || (bound.isEmpty() && p.getName().equalsIgnoreCase("userId"));
    }

    /** Deliberately not a @RestController, so Spring never registers it in other test contexts. */
    @SuppressWarnings("unused")
    static class LeakyController {
        @GetMapping("/api/things")
        public String byUser(@RequestParam UUID userId) {
            return "";
        }

        @GetMapping("/api/audit-logs")
        public String auditLogs(@AuthenticationPrincipal Jwt jwt) {
            return "";
        }
    }
}
