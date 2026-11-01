package com.expenseanalyzer.auth.service;

import java.nio.charset.StandardCharsets;
import com.expenseanalyzer.audit.model.AuditAction;
import com.expenseanalyzer.audit.service.AuditService;
import com.expenseanalyzer.auth.config.RateLimiter;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Password re-verification for sensitive self-service actions, and password change. */
@Service
@RequiredArgsConstructor
public class PasswordService {

    /** BCrypt limit. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private static final String PURPOSE = "pwd";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    /**
     * Counts the attempt against the rate limit (429/503), then checks the password; a wrong one
     * answers 400 with a generic message. A correct one clears the counter.
     */
    public void verifyCurrentPassword(User user, String currentPassword) {
        rateLimiter.check(PURPOSE, user.getId());
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request could not be completed");
        }
        rateLimiter.reset(PURPOSE, user.getId());
    }

    /** Changes the caller's password and revokes all their refresh tokens. */
    @Transactional
    public void changePassword(String callerEmail, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        verifyCurrentPassword(user, currentPassword);
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request could not be completed");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAll(user.getId());
        auditService.record(AuditAction.AUTH_PASSWORD_CHANGED, user.getId());
    }
}
