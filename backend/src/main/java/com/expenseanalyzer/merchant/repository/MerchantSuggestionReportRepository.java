package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.MerchantSuggestionReport;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantSuggestionReportRepository
        extends JpaRepository<MerchantSuggestionReport, MerchantSuggestionReport.Key> {

    @Query("SELECT COUNT(DISTINCT r.userId) FROM MerchantSuggestionReport r "
            + "WHERE r.payeeKey = :payeeKey AND r.merchantId = :merchantId")
    long reporters(String payeeKey, UUID merchantId);

    /** Reports on the given payee keys (by anyone); only ever reduced to a reported/not-reported flag. */
    List<MerchantSuggestionReport> findByPayeeKeyIn(Collection<String> payeeKeys);
}
