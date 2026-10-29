package com.expenseanalyzer.transaction.service;

import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.transaction.dto.TransactionFilter;
import com.expenseanalyzer.transaction.dto.TransactionResponse;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owner-scoped transaction queries. The user always comes from the token, never from a parameter. */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(String email, TransactionFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        UUID userId = currentUser(email).getId();
        Specification<Transaction> spec = (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (f.q() != null && !f.q().isBlank()) {
            String pattern = "%" + escapeLike(f.q().trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("rawDescription")), pattern, '\\'),
                    cb.like(cb.lower(root.get("normalizedDescription")), pattern, '\\')));
        }
        if (f.statementId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("statementId"), f.statementId()));
        }
        if (f.merchantId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("merchantId"), f.merchantId()));
        }
        if (f.category() != null && !f.category().isBlank()) {
            spec = spec.and(categoryIs(f.category()));
        }
        if (f.direction() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), f.direction().name()));
        }
        if (f.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("date"),
                    f.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (f.to() != null) { // inclusive: before the start of the next day
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("date"),
                    f.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (f.minAmount() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("amountCents"), f.minAmount().movePointRight(2)));
        }
        if (f.maxAmount() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("amountCents"), f.maxAmount().movePointRight(2)));
        }
        Page<Transaction> page = transactionRepository.findAll(spec, pageable);
        Map<UUID, Merchant> merchants = merchants(page.getContent());
        return page.map(t -> TransactionResponse.from(t, merchants.get(t.getMerchantId())));
    }

    @Transactional(readOnly = true)
    public Optional<TransactionResponse> find(String email, UUID id) {
        return transactionRepository.findByIdAndUserId(id, currentUser(email).getId())
                .map(t -> TransactionResponse.from(t, merchants(List.of(t)).get(t.getMerchantId())));
    }

    private Map<UUID, Merchant> merchants(List<Transaction> transactions) {
        Set<UUID> ids = transactions.stream().map(Transaction::getMerchantId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return merchantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }

    /** Same category semantics as spending analytics: merchant categoryHint, else "Uncategorized". */
    private static Specification<Transaction> categoryIs(String category) {
        return (root, q, cb) -> {
            Subquery<UUID> sub = q.subquery(UUID.class);
            Root<Merchant> m = sub.from(Merchant.class);
            boolean uncategorized = SpendingAnalyticsService.UNCATEGORIZED.equals(category);
            sub.select(m.get("id")).where(uncategorized ? cb.isNull(m.get("categoryHint"))
                    : cb.equal(m.get("categoryHint"), category));
            Predicate inMerchants = root.get("merchantId").in(sub);
            return uncategorized ? cb.or(cb.isNull(root.get("merchantId")), inMerchants) : inMerchants;
        };
    }

    /** Escapes LIKE wildcards so user text matches literally (escape character is a backslash). */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
