package com.expenseanalyzer.transaction.service;

import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.service.CategoryService;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver.Resolution;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.service.MerchantMatch;
import com.expenseanalyzer.merchant.service.MerchantNormalizer;
import com.expenseanalyzer.merchant.service.MerchantService;
import com.expenseanalyzer.merchant.service.PayeeKey;
import com.expenseanalyzer.merchant.service.PersonService;
import com.expenseanalyzer.category.model.UserMerchantCategory;
import com.expenseanalyzer.transaction.dto.CounterpartyRequest;
import com.expenseanalyzer.transaction.dto.TransactionFilter;
import com.expenseanalyzer.transaction.dto.TransactionResponse;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
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
    private final PersonService personService;
    private final MerchantNormalizer merchantNormalizer;
    private final MerchantService merchantService;

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
            spec = spec.and(categoryIs(f.category(), resolution, userId));
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

    /**
     * Sets who the money went to and applies it to all of the caller's transactions with the same normalized
     * description. PERSON links the (found or created) person and drops the merchant; MERCHANT drops the person and
     * re-runs merchant normalisation on each row (no matching rule leaves no merchant). Raw data is untouched.
     */
    @Transactional
    public TransactionResponse setCounterparty(String email, UUID id, CounterpartyRequest request) {
        UUID userId = currentUser(email).getId();
        Transaction t = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request == null || request.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }
        Person person = request.type() == CounterpartyRequest.Type.PERSON
                ? personService.findOrCreate(userId, PersonService.validName(request.name())) : null;
        List<Transaction> rows = t.getNormalizedDescription() == null ? List.of(t)
                : transactionRepository.findByUserIdAndNormalizedDescription(userId, t.getNormalizedDescription());
        Map<String, UUID> merchantIds = new HashMap<>();
        Map<String, UUID> aliases = person == null ? merchantService.aliases(userId) : new HashMap<>();
        Instant now = Instant.now();
        for (Transaction row : rows) {
            row.setPersonId(person == null ? null : person.getId());
            row.setMerchantId(null);
            row.setMerchantConfidence(null);
            if (person == null) {
                MerchantMatch m = merchantNormalizer.normalize(row.getRawDescription());
                UUID named = aliases.get(PayeeKey.of(row.getRawDescription(), m.normalizedDescription()));
                if (named != null) {
                    row.setMerchantId(named);
                } else if (m.canonicalName() != null) {
                    row.setMerchantId(merchantIds.computeIfAbsent(m.canonicalName(), n -> merchantService.resolve(m).getId()));
                    row.setMerchantConfidence(m.confidence());
                }
            }
            row.setUpdatedAt(now);
        }
        transactionRepository.saveAll(rows);
        return response(t, userId);
    }

    /**
     * Names the merchant of an unmatched payment: finds or creates the merchant, remembers it for the payee and
     * applies it (dropping any person) to all of the caller's transactions with the same payee key. Raw data is untouched.
     */
    @Transactional
    public TransactionResponse setMerchant(String email, UUID id, String name) {
        UUID userId = currentUser(email).getId();
        Transaction t = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String clean = PersonService.validName(name);
        String key = PayeeKey.of(t.getRawDescription(), t.getNormalizedDescription());
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction has no payee");
        }
        UUID merchantId = merchantService.resolve(new MerchantMatch(clean, clean, null, null)).getId();
        merchantService.saveAlias(userId, key, merchantId);
        Instant now = Instant.now();
        List<Transaction> rows = transactionRepository.findByUserId(userId).stream()
                .filter(r -> key.equals(PayeeKey.of(r.getRawDescription(), r.getNormalizedDescription()))).toList();
        for (Transaction row : rows) {
            row.setMerchantId(merchantId);
            row.setMerchantConfidence(null);
            row.setPersonId(null);
            row.setUpdatedAt(now);
        }
        transactionRepository.saveAll(rows);
        return response(t, userId);
    }

    private TransactionResponse response(Transaction t, UUID userId) {
        return response(t, merchants(List.of(t)).get(t.getMerchantId()), categoryResolver.forUser(userId));
    }

    private static TransactionResponse response(Transaction t, Merchant merchant, Resolution resolution) {
        return TransactionResponse.from(t, merchant, resolution.person(t), resolution.resolve(t, merchant));
    }

    private Map<UUID, Merchant> merchants(List<Transaction> transactions) {
        Set<UUID> ids = transactions.stream().map(Transaction::getMerchantId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return merchantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }

    /**
     * Filters by effective category name (case-insensitive). This is the SQL form of {@link Resolution#resolve}; keep
     * them in sync. Order: own category (only if it is one of the user's), else the person's category, else the
     * merchant mapping, else a merchant categoryHint naming one of the user's categories, else Uncategorized.
     * A categoryId (or mapping, or person category) that is not one of the user's categories counts as not set.
     * Everything is matched through subqueries; no id lists are bound.
     */
    private Specification<Transaction> categoryIs(String name, Resolution resolution, UUID userId) {
        boolean uncategorized = SpendingAnalyticsService.UNCATEGORIZED.equalsIgnoreCase(name.trim());
        Category category = uncategorized ? null : resolution.named(name.trim());
        if (!uncategorized && category == null) {
            return (root, q, cb) -> cb.disjunction();
        }
        UUID categoryId = uncategorized ? null : category.getId();
        String lowerName = uncategorized ? null : category.getName().toLowerCase(Locale.ROOT);
        return (root, q, cb) -> {
            Predicate ownValid = in(cb, root.get("categoryId"), userCategories(q, cb, userId));
            Predicate personValid = in(cb, root.get("personId"), people(q, cb, userId, null));
            Predicate mappedValid = in(cb, root.get("merchantId"), mappings(q, cb, userId, null));
            if (uncategorized) {
                return cb.and(cb.not(ownValid), cb.not(personValid), cb.not(mappedValid),
                        cb.not(in(cb, root.get("merchantId"), hinted(q, cb, userId, null))));
            }
            Predicate viaPerson = in(cb, root.get("personId"), people(q, cb, userId, categoryId));
            Predicate viaMapping = in(cb, root.get("merchantId"), mappings(q, cb, userId, categoryId));
            Predicate viaHint = cb.and(in(cb, root.get("merchantId"), hinted(q, cb, userId, lowerName)),
                    cb.not(mappedValid));
            return cb.or(cb.equal(root.get("categoryId"), categoryId),
                    cb.and(cb.not(ownValid), cb.or(viaPerson, cb.and(cb.not(personValid), cb.or(viaMapping, viaHint)))));
        };
    }

    /** Null-safe IN: a null column is simply "not in", never SQL NULL (which would break NOT). */
    private static Predicate in(CriteriaBuilder cb, Path<UUID> column, Subquery<UUID> ids) {
        return cb.and(cb.isNotNull(column), column.in(ids));
    }

    private static Subquery<UUID> userCategories(AbstractQuery<?> q, CriteriaBuilder cb, UUID userId) {
        Subquery<UUID> sq = q.subquery(UUID.class);
        Root<Category> c = sq.from(Category.class);
        return sq.select(c.get("id")).where(cb.equal(c.get("userId"), userId));
    }

    /** The user's people whose category is {@code categoryId}, or any of the user's categories when null. */
    private static Subquery<UUID> people(AbstractQuery<?> q, CriteriaBuilder cb, UUID userId, UUID categoryId) {
        Subquery<UUID> sq = q.subquery(UUID.class);
        Root<Person> p = sq.from(Person.class);
        return sq.select(p.get("id")).where(cb.equal(p.get("userId"), userId),
                categoryId == null ? p.<UUID>get("categoryId").in(userCategories(sq, cb, userId))
                        : cb.equal(p.get("categoryId"), categoryId));
    }

    /** Merchants the user mapped to {@code categoryId}, or to any of the user's categories when null. */
    private static Subquery<UUID> mappings(AbstractQuery<?> q, CriteriaBuilder cb, UUID userId, UUID categoryId) {
        Subquery<UUID> sq = q.subquery(UUID.class);
        Root<UserMerchantCategory> m = sq.from(UserMerchantCategory.class);
        return sq.select(m.get("merchantId")).where(cb.equal(m.get("userId"), userId),
                categoryId == null ? m.<UUID>get("categoryId").in(userCategories(sq, cb, userId))
                        : cb.equal(m.get("categoryId"), categoryId));
    }

    /** Merchants whose categoryHint equals {@code lowerName}, or names any of the user's categories when null. */
    private static Subquery<UUID> hinted(AbstractQuery<?> q, CriteriaBuilder cb, UUID userId, String lowerName) {
        Subquery<UUID> sq = q.subquery(UUID.class);
        Root<Merchant> m = sq.from(Merchant.class);
        Expression<String> hint = cb.lower(m.get("categoryHint"));
        if (lowerName != null) {
            return sq.select(m.get("id")).where(cb.equal(hint, lowerName));
        }
        Subquery<String> names = sq.subquery(String.class);
        Root<Category> c = names.from(Category.class);
        names.select(cb.lower(c.get("name"))).where(cb.equal(c.get("userId"), userId));
        return sq.select(m.get("id")).where(hint.in(names));
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
