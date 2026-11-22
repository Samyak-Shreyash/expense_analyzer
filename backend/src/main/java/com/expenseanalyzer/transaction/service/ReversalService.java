package com.expenseanalyzer.transaction.service;

import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pairs a refund or reversal with the payment it undoes: a CREDIT of the same amount and currency dated 0 to 30 days
 * after a DEBIT, with the same merchant or a shared word in the description. One rupee or less needs no shared
 * evidence but a gap of at most 3 days (verification payments). Pairs are automatic (not manual); rows the user
 * touched, transfers and duplicates are left alone. Raw data is untouched; nothing is logged.
 */
@Service
@RequiredArgsConstructor
public class ReversalService {

    static final Duration WINDOW = Duration.ofDays(30);
    static final Duration SMALL_WINDOW = Duration.ofDays(3);
    private static final BigDecimal SMALL_PAISE = BigDecimal.valueOf(100);
    private static final int MIN_TOKEN = 4;
    private static final Set<String> STOP = Set.of("BANK", "PAYMENT", "PAYMENTS", "PAID", "REFUND", "REVERSAL", "FROM",
            "LIMITED", "PVT", "LTD", "INDIA", "SMALL", "FINANCE");

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /** Pairs the user's open rows; debits in date order take the eligible credit with the smallest date gap. */
    @Transactional
    public void match(UUID userId) {
        String fullName = userRepository.findById(userId).map(User::getFullName).orElse(null);
        Set<String> ownTokens = tokens(fullName, 1);
        List<Transaction> open = transactionRepository
                .findByUserIdAndTransferFalseAndTransferManualFalseAndDuplicateFalseAndReversalPairIdIsNull(userId);
        List<Transaction> credits = open.stream().filter(t -> "CREDIT".equals(t.getType())).toList();
        List<Transaction> debits = open.stream().filter(t -> "DEBIT".equals(t.getType()))
                .sorted(Comparator.comparing(Transaction::getDate).thenComparing(Transaction::getCreatedAt)
                        .thenComparing(Transaction::getId))
                .toList();
        Set<UUID> used = new HashSet<>();
        List<Transaction> paired = new ArrayList<>();
        Instant now = Instant.now();
        for (Transaction debit : debits) {
            Set<String> words = words(debit, fullName, ownTokens);
            Optional<Transaction> match = credits.stream()
                    .filter(c -> !used.contains(c.getId()) && eligible(debit, c, words, fullName, ownTokens))
                    .min(Comparator.comparing((Transaction c) -> Duration.between(debit.getDate(), c.getDate()))
                            .thenComparing(Transaction::getCreatedAt).thenComparing(Transaction::getId));
            match.ifPresent(credit -> {
                used.add(credit.getId());
                pair(debit, credit, now);
                paired.add(debit);
                paired.add(credit);
            });
        }
        transactionRepository.saveAll(paired);
    }

    /** Releases the other halves of the pairs (manual or automatic) before a statement's rows are deleted. */
    @Transactional
    public void releasePartners(UUID userId, UUID statementId) {
        Set<UUID> partners = transactionRepository.findByUserIdAndStatementId(userId, statementId).stream()
                .map(Transaction::getReversalPairId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Transaction> released = transactionRepository.findAllById(partners).stream()
                .filter(t -> !statementId.equals(t.getStatementId())).toList();
        Instant now = Instant.now();
        released.forEach(t -> {
            t.setReversalPairId(null);
            t.setReversalChargePaise(null);
            t.setUpdatedAt(now);
        });
        transactionRepository.saveAll(released);
    }

    private static boolean eligible(Transaction debit, Transaction credit, Set<String> debitWords, String fullName,
            Set<String> ownTokens) {
        if (debit.getAmountCents().compareTo(credit.getAmountCents()) != 0
                || !Objects.equals(debit.getCurrency(), credit.getCurrency())) {
            return false;
        }
        Duration gap = Duration.between(debit.getDate(), credit.getDate());
        if (gap.isNegative() || gap.compareTo(WINDOW) > 0) {
            return false;
        }
        if (debit.getAmountCents().compareTo(SMALL_PAISE) <= 0 && gap.compareTo(SMALL_WINDOW) <= 0) {
            return true;
        }
        if (debit.getMerchantId() != null && debit.getMerchantId().equals(credit.getMerchantId())) {
            return true;
        }
        return words(credit, fullName, ownTokens).stream().anyMatch(debitWords::contains);
    }

    /** Description words that can serve as evidence: long enough, not boilerplate, not the user's own name. */
    private static Set<String> words(Transaction t, String fullName, Set<String> ownTokens) {
        return tokens(t.getNormalizedDescription(), MIN_TOKEN).stream()
                .filter(w -> !STOP.contains(w) && !ownTokens.contains(w) && !OwnName.mentions(fullName, w))
                .collect(Collectors.toSet());
    }

    private static Set<String> tokens(String s, int minLength) {
        if (s == null) {
            return Set.of();
        }
        return Arrays.stream(s.toUpperCase(Locale.ROOT).split("[^\\p{L}]+")).filter(w -> w.length() >= minLength)
                .collect(Collectors.toSet());
    }

    private static void pair(Transaction a, Transaction b, Instant now) {
        for (Transaction[] t : new Transaction[][] {{a, b}, {b, a}}) {
            t[0].setReversalPairId(t[1].getId());
            t[0].setReversalPending(false);
            t[0].setTransfer(false);
            t[0].setUpdatedAt(now);
        }
    }
}
