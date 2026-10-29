package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.UserMerchantAlias;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserMerchantAliasRepository extends JpaRepository<UserMerchantAlias, UserMerchantAlias.Key> {

    List<UserMerchantAlias> findByUserId(UUID userId);
}
