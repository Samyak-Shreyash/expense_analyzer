package com.expenseanalyzer.user.repository;

import com.expenseanalyzer.user.model.Currency;
import com.expenseanalyzer.user.model.NotificationMode;
import com.expenseanalyzer.user.model.Preference;
import com.expenseanalyzer.user.model.Theme;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user preferences.
 */
public interface PreferenceRepository extends JpaRepository<Preference, UUID> {

    /**
     * Typed columns only. The jsonb "value" column is deliberately never read: it does not
     * round-trip through Hibernate on the H2 test database, and nothing needs it.
     */
    interface View {
        String getKey();
        Theme getTheme();
        Currency getCurrency();
        NotificationMode getNotificationMode();
    }

    List<View> findViewsByUserId(UUID userId);

    Optional<View> findViewByUserIdAndKey(UUID userId, String key);

    @Modifying
    @Query("update Preference p set p.theme = :theme, p.currency = :currency, p.notificationMode = :mode "
            + "where p.userId = :userId and p.key = :key")
    int updateTyped(@Param("userId") UUID userId, @Param("key") String key, @Param("theme") Theme theme,
            @Param("currency") Currency currency, @Param("mode") NotificationMode mode);
}
