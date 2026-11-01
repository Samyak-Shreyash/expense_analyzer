package com.expenseanalyzer.messaging.repository;

import com.expenseanalyzer.messaging.model.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByPublishedAtIsNullAndAttemptCountLessThanOrderByCreatedAtAsc(int maxAttempts);

    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.publishedAt = :publishedAt, e.attemptCount = :attemptCount, "
            + "e.lastError = :lastError WHERE e.id = :id")
    void updateDeliveryState(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt,
            @Param("attemptCount") int attemptCount, @Param("lastError") String lastError);
}
