package com.expenseanalyzer.transaction.service;

import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.service.CategoryService;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver.Resolution;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.transaction.dto.TransactionFilter;
import com.expenseanalyzer.transaction.dto.TransactionResponse;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    private final CategoryService categoryService;
    private final EffectiveCategoryResolver categoryResolver;

    @Transactional // not readOnly: first access seeds the user's default categories
    public Page<TransactionResponse> list(String email, TransactionFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        UUID userId = currentUser(email).getId();
        Resolution resolution = categoryResolver.forUser(userId);
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
            spec = spec.and(categoryIs(f.category(), resolution));
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
        return page.map(t -> response(t, merchants.get(t.getMerchantId()), resolution));
    }

    @Transactional
    public Optional<TransactionResponse> find(String email, UUID id) {
        UUID userId = currentUser(email).getId();
        return transactionRepository.findByIdAndUserId(id, userId).map(t -> response(t, userId));
    }

    /** Sets (or, with a null categoryId, clears) the transaction's own category. Not yours or unknown: 404. */
    @Transactional
    public TransactionResponse setCategory(String email, UUID id, UUID categoryId) {
        UUID userId = currentUser(email).getId();
        Transaction t = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (categoryId != null) {
            categoryService.owned(userId, categoryId);
        }
        t.setCategoryId(categoryId);
        t.setUpdatedAt(Instant.now());
        return response(transactionRepository.save(t), userId);
    }

    private TransactionResponse response(Transaction t, UUID userId) {
        return response(t, merchants(List.of(t)).get(t.getMerchantId()), categoryResolver.forUser(userId));
    }

    private static TransactionResponse response(Transaction t, Merchant merchant, Resolution resolution) {
        return TransactionResponse.from(t, merchant, resolution.resolve(t, merchant));
    }

    private Map<UUID, Merchant> merchants(List<Transaction> transactions) {
        Set<UUID> ids = transactions.stream().map(Transaction::getMerchantId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return merchantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }

    /**
     * Filters by effective category name (case-insensitive), mirroring {@link Resolution#resolve}: own category; else
     * merchant mapping; else merchant categoryHint naming one of the user's categories. The person step goes here too.
     */
    private Specification<Transaction> categoryIs(String name, Resolution resolution) {
        List<UUID> hinted = merchantRepository.findIdsByCategoryHintLower(resolution.lowerNames());
        Set<UUID> mapped = resolution.mappedMerchants();
        if (SpendingAnalyticsService.UNCATEGORIZED.equalsIgnoreCase(name.trim())) {
            return (root, q, cb) -> {
                List<Predicate> unresolved = new ArrayList<>();
                unresolved.add(cb.isNull(root.get("categoryId")));
                Predicate noMerchant = cb.isNull(root.get("merchantId"));
                List<Predicate> merchantUnresolved = new ArrayList<>();
                if (!mapped.isEmpty()) {
                    merchantUnresolved.add(cb.not(root.get("merchantId").in(mapped)));
                }
                if (!hinted.isEmpty()) {
                    merchantUnresolved.add(cb.not(root.get("merchantId").in(hinted)));
                }
                unresolved.add(cb.or(noMerchant, cb.and(merchantUnresolved.toArray(Predicate[]::new))));
                return cb.and(unresolved.toArray(Predicate[]::new));
            };
        }
        Category category = resolution.named(name.trim());
        if (category == null) {
            return (root, q, cb) -> cb.disjunction();
        }
        Set<UUID> mappedHere = resolution.merchantsMappedTo(category.getId());
        List<UUID> hintedHere = merchantRepository.findIdsByCategoryHintLower(Set.of(name.trim().toLowerCase(Locale.ROOT)));
        return (root, q, cb) -> {
            List<Predicate> viaMerchant = new ArrayList<>();
            if (!mappedHere.isEmpty()) {
                viaMerchant.add(root.get("merchantId").in(mappedHere));
            }
            if (!hintedHere.isEmpty()) {
                Predicate hintMatches = root.get("merchantId").in(hintedHere);
                viaMerchant.add(mapped.isEmpty() ? hintMatches : cb.and(hintMatches, cb.not(root.get("merchantId").in(mapped))));
            }
            Predicate own = cb.equal(root.get("categoryId"), category.getId());
            if (viaMerchant.isEmpty()) {
                return own;
            }
            return cb.or(own, cb.and(cb.isNull(root.get("categoryId")), cb.or(viaMerchant.toArray(Predicate[]::new))));
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
