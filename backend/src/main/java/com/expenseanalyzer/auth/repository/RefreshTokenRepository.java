package com.expenseanalyzer.auth.repository;

import com.expenseanalyzer.auth.model.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    void deleteByUserId(UUID userId);

    void deleteByUserIdAndExpiresAtBefore(UUID userId, Instant cutoff);

    /** 1 for the one caller that consumed the token; 0 if another request got there first. */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.consumedAt = :now WHERE t.id = :id AND t.consumedAt IS NULL")
    int markConsumed(@Param("id") UUID id, @Param("now") Instant now);

    /** 1 for the one caller allowed to replay the rotated token; 0 if it was already replayed. */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.replayedAt = :now WHERE t.id = :id AND t.consumedAt IS NOT NULL AND t.replayedAt IS NULL")
    int markReplayed(@Param("id") UUID id, @Param("now") Instant now);
}
