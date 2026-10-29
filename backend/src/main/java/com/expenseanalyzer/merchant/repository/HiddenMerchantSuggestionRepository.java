package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.HiddenMerchantSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HiddenMerchantSuggestionRepository
        extends JpaRepository<HiddenMerchantSuggestion, HiddenMerchantSuggestion.Key> {
}
