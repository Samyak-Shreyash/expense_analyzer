package com.expenseanalyzer.user.repository;

import com.expenseanalyzer.user.model.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    List<UserAccount> findByUserIdOrderByCreatedAt(UUID userId);

    Optional<UserAccount> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndAccountNumber(UUID userId, String accountNumber);
}
