package com.expenseanalyzer.transaction.service;

import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pairs money moved between a user's own accounts (DOMAIN-MODEL 4.3): a DEBIT in one account and a CREDIT of the same
 * amount and currency in a different account, at most {@link #WINDOW} apart. An account is a statement's
 * (account_number, bank_name) until an accounts table exists, so rows without one are never paired automatically.
 * Rows the user marked or unmarked, rows already paired, and rows with a merchant or person are left alone. Raw data is untouched; nothing is logged.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    static final Duration WINDOW = Duration.ofDays(3);

    private final TransactionRepository transactionRepository;
    private final StatementRepository statementRepository;

    /** Pairs the user's open rows; debits in date order take the credit with the closest date (ties: earliest created). */
    @Transactional
    public void match(UUID userId) {
        List<Transaction> open = transactionRepository.findByUserIdAndTransferFalseAndTransferManualFalse(userId).stream()
                .filter(t -> t.getMerchantId() == null && t.getPersonId() == null).toList();
        Map<UUID, String> accounts = statementRepository.findAllById(open.stream().map(Transaction::getStatementId)
                        .filter(id -> id != null).collect(Collectors.toSet())).stream()
                .filter(s -> s.getAccountNumber() != null && !s.getAccountNumber().isBlank())
                .collect(Collectors.toMap(Statement::getId, TransferService::account));
        Map<String, List<Transaction>> credits = open.stream()
                .filter(t -> "CREDIT".equals(t.getType()) && accounts.containsKey(t.getStatementId()))
                .collect(Collectors.groupingBy(TransferService::amountKey));
        List<Transaction> debits = open.stream()
                .filter(t -> "DEBIT".equals(t.getType()) && accounts.containsKey(t.getStatementId()))
                .sorted(Comparator.comparing(Transaction::getDate).thenComparing(Transaction::getCreatedAt))
                .toList();
        Set<UUID> used = new HashSet<>();
        List<Transaction> paired = new ArrayList<>();
        Instant now = Instant.now();
        for (Transaction debit : debits) {
            String account = accounts.get(debit.getStatementId());
            Optional<Transaction> match = credits.getOrDefault(amountKey(debit), List.of()).stream()
                    .filter(c -> !used.contains(c.getId()) && !account.equals(accounts.get(c.getStatementId())))
                    .filter(c -> gap(debit, c).compareTo(WINDOW) <= 0)
                    .min(Comparator.comparing((Transaction c) -> gap(debit, c)).thenComparing(Transaction::getCreatedAt)
                            .thenComparing(Transaction::getId));
            match.ifPresent(credit -> {
                used.add(credit.getId());
                pair(debit, credit, false, now);
                paired.add(debit);
                paired.add(credit);
            });
        }
        transactionRepository.saveAll(paired);
    }

    /** Reopens the automatic pairs' other halves before a statement's rows are deleted; manual marks are kept. */
    @Transactional
    public void releasePartners(UUID userId, UUID statementId) {
        Set<UUID> partners = transactionRepository.findByUserIdAndStatementId(userId, statementId).stream()
                .map(Transaction::getTransferPairId).filter(id -> id != null).collect(Collectors.toSet());
        List<Transaction> released = transactionRepository.findAllById(partners).stream()
                .filter(t -> !statementId.equals(t.getStatementId()) && !t.isTransferManual()).toList();
        Instant now = Instant.now();
        released.forEach(t -> {
            t.setTransfer(false);
            t.setTransferPairId(null);
            t.setUpdatedAt(now);
        });
        transactionRepository.saveAll(released);
    }

    /** Links two transactions as the halves of one transfer. */
    static void pair(Transaction a, Transaction b, boolean manual, Instant now) {
        for (Transaction[] t : new Transaction[][] {{a, b}, {b, a}}) {
            t[0].setTransfer(true);
            t[0].setTransferPairId(t[1].getId());
            t[0].setTransferManual(manual);
            t[0].setUpdatedAt(now);
        }
    }

    private static Duration gap(Transaction a, Transaction b) {
        return Duration.between(a.getDate(), b.getDate()).abs();
    }

    private static String amountKey(Transaction t) {
        return t.getAmount().stripTrailingZeros().toPlainString() + " " + t.getCurrency();
    }

    private static String account(Statement s) {
        String bank = s.getBankName() == null ? "" : s.getBankName().trim().toLowerCase(Locale.ROOT);
        return s.getAccountNumber().trim().toLowerCase(Locale.ROOT) + "|" + bank;
    }
}
