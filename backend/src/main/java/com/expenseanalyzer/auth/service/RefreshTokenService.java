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
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, rotates and revokes opaque refresh tokens. A refresh rotates the token only once it is older than
 * {@code rotateAfterSeconds}, so reloads in quick succession reuse it and rarely abort a rotation. A rotated token is
 * consumed and linked to its replacement until the client first uses the replacement: until then a replay supersedes
 * the replacement, since the client may never have received it (a page reload aborts the response that carried it).
 * After that, replaying it means it leaked, so all the user's tokens are revoked; replays within {@link #REUSE_GRACE}
 * are only refused, since two browser tabs can refresh with the same cookie at once.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final long refreshTokenSeconds;
    private final long rotateAfterSeconds;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               @Value("${app.oauth.jwt.refreshTokenExpirationSeconds:604800}") long refreshTokenSeconds,
                               @Value("${app.oauth.jwt.refreshTokenRotateAfterSeconds:60}") long rotateAfterSeconds) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenSeconds = refreshTokenSeconds;
        this.rotateAfterSeconds = rotateAfterSeconds;
    }

    /** The token's owner and the refresh token the client keeps: the presented one unless it was rotated. */
    public record Refreshed(User user, String refreshToken) {
    }

    private record Issued(UUID id, String raw) {
    }

    public long getRefreshTokenSeconds() {
        return refreshTokenSeconds;
    }

    /** Creates a refresh token for the user and returns its raw value. */
    @Transactional
    public String issue(User user) {
        return create(user.getId()).raw();
    }

    /** Refreshes a valid token; empty if unknown, expired, already rotated or the owner is inactive. */
    @Transactional
    public Optional<Refreshed> refresh(String raw) {
        return refreshTokenRepository.findByTokenHash(hash(raw))
                .flatMap(token -> {
                    Instant now = Instant.now();
                    if (token.getConsumedAt() != null) {
                        if (token.getReplacedById() != null && !token.getExpiresAt().isBefore(now)) {
                            return supersede(token, now);
                        }
                        if (token.getConsumedAt().isBefore(now.minus(REUSE_GRACE))) {
                            refreshTokenRepository.deleteByUserId(token.getUserId());
                        }
                        return Optional.empty();
                    }
                    if (token.getExpiresAt().isBefore(now)) {
                        refreshTokenRepository.delete(token);
                        return Optional.empty();
                    }
                    refreshTokenRepository.confirmReplacement(token.getId());
                    if (token.getCreatedAt().isAfter(now.minusSeconds(rotateAfterSeconds))) {
                        return owner(token).map(user -> new Refreshed(user, raw));
                    }
                    if (refreshTokenRepository.markConsumed(token.getId(), now) == 0) {
                        return Optional.empty();
                    }
                    return replace(token);
                });
    }

    /** The client never used the token's replacement: retire it and issue another. */
    private Optional<Refreshed> supersede(RefreshToken token, Instant now) {
        if (refreshTokenRepository.markConsumed(token.getReplacedById(), now) == 0) {
            return Optional.empty(); // a concurrent replay superseded it first, or it was revoked
        }
        return replace(token);
    }

    private Optional<Refreshed> replace(RefreshToken token) {
        return owner(token).map(user -> {
            Issued replacement = create(user.getId());
            refreshTokenRepository.linkReplacement(token.getId(), replacement.id());
            return new Refreshed(user, replacement.raw());
        });
    }

    private Optional<User> owner(RefreshToken token) {
        return userRepository.findById(token.getUserId()).filter(User::isEnabled);
    }

    private Issued create(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.deleteByUserIdAndExpiresAtBefore(userId, Instant.now());
        RefreshToken saved = refreshTokenRepository.save(
                new RefreshToken(userId, hash(raw), Instant.now().plusSeconds(refreshTokenSeconds)));
        return new Issued(saved.getId(), raw);
    }

    /**
     * True when the token was rotated within {@link #REUSE_GRACE} or its replacement is still unused: another tab or
     * request refreshed with it first, so the caller already holds (or is about to receive) the replacement. Never true
     * for an inactive owner.
     */
    @Transactional(readOnly = true)
    public boolean recentlyRotated(String raw) {
        Instant cutoff = Instant.now().minus(REUSE_GRACE);
        return refreshTokenRepository.findByTokenHash(hash(raw))
                .filter(t -> t.getConsumedAt() != null
                        && (t.getReplacedById() != null || !t.getConsumedAt().isBefore(cutoff)))
                .flatMap(t -> userRepository.findById(t.getUserId()))
                .filter(User::isEnabled)
                .isPresent();
    }

    @Transactional
    public void revoke(String raw) {
        refreshTokenRepository.deleteByTokenHash(hash(raw));
    }

    /** Revokes every refresh token of the user. */
    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
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
