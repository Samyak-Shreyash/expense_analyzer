package com.expenseanalyzer.auth.service;

import com.expenseanalyzer.auth.model.RefreshToken;
import com.expenseanalyzer.auth.repository.RefreshTokenRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues, rotates and revokes opaque refresh tokens. Each token is single-use:
 * a successful rotation deletes it and issues a new one.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final long refreshTokenSeconds;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               @Value("${app.oauth.jwt.refreshTokenExpirationSeconds:604800}") long refreshTokenSeconds) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenSeconds = refreshTokenSeconds;
    }

    public long getRefreshTokenSeconds() {
        return refreshTokenSeconds;
    }

    /** Creates a refresh token for the user and returns its raw value. */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(new RefreshToken(user.getId(), hash(raw), Instant.now().plusSeconds(refreshTokenSeconds)));
        return raw;
    }

    /** Consumes a valid token and returns its owner; empty if unknown or expired. */
    @Transactional
    public Optional<User> consume(String raw) {
        return refreshTokenRepository.findByTokenHash(hash(raw))
                .flatMap(token -> {
                    refreshTokenRepository.delete(token);
                    if (token.getExpiresAt().isBefore(Instant.now())) {
                        return Optional.empty();
                    }
                    return userRepository.findById(token.getUserId());
                });
    }

    @Transactional
    public void revoke(String raw) {
        refreshTokenRepository.deleteByTokenHash(hash(raw));
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // every JVM ships it
        }
    }
}
