package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.user.model.User;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Reads and writes only the merchant-name sharing flags of users. */
public interface SharingSettingsRepository extends Repository<User, UUID> {

    record Settings(boolean shareMerchantNames, boolean sharingRevoked) {

        public boolean sharing() {
            return shareMerchantNames && !sharingRevoked;
        }
    }

    @Query("SELECT new com.expenseanalyzer.merchant.repository.SharingSettingsRepository$Settings("
            + "u.shareMerchantNames, u.sharingRevoked) FROM User u WHERE u.id = :userId")
    Optional<Settings> find(UUID userId);

    /** The user's flags; an unknown user shares nothing. */
    default Settings settings(UUID userId) {
        return find(userId).orElse(new Settings(false, false));
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.shareMerchantNames = :enabled WHERE u.id = :userId")
    int setSharing(UUID userId, boolean enabled);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.sharingRevoked = false WHERE u.id = :userId")
    int clearRevoked(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.sharingRevoked = true, u.shareMerchantNames = false WHERE u.id IN :userIds")
    int revoke(Collection<UUID> userIds);
}
