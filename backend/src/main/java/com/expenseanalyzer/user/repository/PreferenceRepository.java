package com.expenseanalyzer.user.repository;

import com.expenseanalyzer.user.model.Preference;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user preferences.
 */
public interface PreferenceRepository extends JpaRepository<Preference, UUID> {

    List<Preference> findByUserId(UUID userId);

    Optional<Preference> findByUserIdAndKey(UUID userId, String key);
}
