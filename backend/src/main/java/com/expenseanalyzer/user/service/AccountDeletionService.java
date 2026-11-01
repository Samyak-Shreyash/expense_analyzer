package com.expenseanalyzer.user.service;

import com.expenseanalyzer.audit.model.AuditAction;
import com.expenseanalyzer.audit.service.AuditService;
import com.expenseanalyzer.auth.service.PasswordService;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.S3StatementStorage;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deletes the caller's account per docs/architecture/ADR/SECURITY-REQUIREMENTS.md section 33.1: stored files first, so
 * a storage failure (503) leaves every row in place and the request can be retried, then all rows in one transaction.
 * Audit rows stay with the user id removed; shared merchants stay.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    /** Every table holding the user's rows, children first. Add new user-owned tables here. */
    private static final List<String> OWNED_TABLES = List.of(
            "outbox_events", "transactions", "statements", "person_payees", "people", "user_merchant_categories",
            "categories", "preferences", "refresh_tokens", "user_merchant_aliases", "merchant_suggestion_votes",
            "merchant_suggestion_reports", "sharing_strikes");

    private final UserRepository userRepository;
    private final StatementRepository statementRepository;
    private final S3StatementStorage storage;
    private final PasswordService passwordService;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public void delete(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        passwordService.verifyCurrentPassword(user, password);
        UUID userId = user.getId();
        Set<String> deletedKeys = new HashSet<>();
        deleteFiles(userId, deletedKeys);
        transaction.executeWithoutResult(status -> {
            deleteFiles(userId, deletedKeys); // a statement uploaded since the first pass
            jdbc.update("UPDATE audit_logs SET user_id = NULL WHERE user_id = ?", userId);
            OWNED_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", userId));
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
            auditService.record(AuditAction.ACCOUNT_DELETED, null);
        });
    }

    private void deleteFiles(UUID userId, Set<String> deletedKeys) {
        statementRepository.findByUserId(userId).stream()
                .map(Statement::getStorageKey)
                .filter(Objects::nonNull)
                .filter(key -> !deletedKeys.contains(key))
                .forEach(key -> {
                    storage.delete(key);
                    deletedKeys.add(key);
                });
    }
}
