package com.expenseanalyzer.messaging.repository;

import com.expenseanalyzer.messaging.model.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByPublishedAtIsNullAndAttemptCountLessThanOrderByCreatedAtAsc(int maxAttempts);
}
