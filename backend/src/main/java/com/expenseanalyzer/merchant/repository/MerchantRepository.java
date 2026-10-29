package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.Merchant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByCanonicalName(String canonicalName);

    /** Ids of merchants whose categoryHint equals any of these lower-cased names. */
    @Query("SELECT m.id FROM Merchant m WHERE lower(m.categoryHint) IN :lowerNames")
    List<UUID> findIdsByCategoryHintLower(Collection<String> lowerNames);
}
