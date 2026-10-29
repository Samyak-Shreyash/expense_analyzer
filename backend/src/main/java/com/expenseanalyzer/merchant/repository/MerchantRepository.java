package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.Merchant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByCanonicalName(String canonicalName);

    Optional<Merchant> findFirstByCanonicalNameIgnoreCaseOrderByCreatedAtAsc(String canonicalName);
}
