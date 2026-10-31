package com.expenseanalyzer.transaction.service;

import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.model.UserMerchantCategory;
import com.expenseanalyzer.category.service.CategoryService;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver.Resolution;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.model.UserMerchantAlias;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository;
import com.expenseanalyzer.merchant.service.MerchantMatch;
import com.expenseanalyzer.merchant.service.MerchantNameService;
import com.expenseanalyzer.merchant.service.MerchantNormalizer;
import com.expenseanalyzer.merchant.service.MerchantService;
import com.expenseanalyzer.merchant.service.PayeeKey;
import com.expenseanalyzer.merchant.service.PersonService;
import com.expenseanalyzer.transaction.dto.CounterpartyRequest;
import com.expenseanalyzer.transaction.dto.TransactionFilter;
import com.expenseanalyzer.transaction.dto.TransactionResponse;
import com.expenseanalyzer.transaction.dto.TransferRequest;
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
    private final MerchantNameService merchantNameService;
    private final UserMerchantAliasRepository aliasRepository;

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
            spec = spec.and(categoryIs(f.category().trim(), resolution, userId));
        }
        if (f.transfer() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("transfer"), f.transfer()));
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
        Map<UUID, String> suggested = suggestions(userId, page.getContent());
        Map<UUID, String> ownNames = ownMerchantNames(userId, page.getContent());
        return page.map(t -> response(t, merchants.get(t.getMerchantId()), ownNames.get(t.getId()), resolution,
                suggested.get(t.getId())));
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
     * Marks or unmarks a transaction as money moved between the caller's own accounts; the user's choice sticks
     * (automatic matching skips it afterwards). Unmarking a pair unmarks both halves. Marking with {@code pairWithId}
     * pairs it with that transaction, which must be the caller's, in the other direction, with the same amount and
     * currency (else 400); any earlier partner of either is released back to automatic matching. Not yours: 404.
     */
    @Transactional
    public TransactionResponse setTransfer(String email, UUID id, TransferRequest request) {
        UUID userId = currentUser(email).getId();
        Transaction t = owned(id, userId);
        Instant now = Instant.now();
        if (!request.transfer()) {
            release(t, userId, true, now);
            t.setTransfer(false);
            t.setTransferManual(true);
        } else if (request.pairWithId() != null) {
            Transaction other = owned(request.pairWithId(), userId);
            if (other.getId().equals(t.getId()) || other.getType().equals(t.getType())
                    || other.getAmount().compareTo(t.getAmount()) != 0 || !other.getCurrency().equals(t.getCurrency())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A transfer pairs money out and money in of the same amount");
            }
            if (!other.getId().equals(t.getTransferPairId())) {
                release(t, userId, false, now);
                release(other, userId, false, now);
            }
            TransferService.pair(t, other, true, now);
            transactionRepository.save(other);
        } else {
            release(t, userId, false, now);
            t.setTransfer(true);
            t.setTransferManual(true);
        }
        t.setUpdatedAt(now);
        return response(transactionRepository.save(t), userId);
    }

    /** Unlinks the transaction's partner, if any; {@code manual} records the partner as the user's choice too. */
    private void release(Transaction t, UUID userId, boolean manual, Instant now) {
        if (t.getTransferPairId() == null) {
            return;
        }
        transactionRepository.findByIdAndUserId(t.getTransferPairId(), userId).ifPresent(partner -> {
            partner.setTransfer(false);
            partner.setTransferPairId(null);
            partner.setTransferManual(manual);
            partner.setUpdatedAt(now);
            transactionRepository.save(partner);
        });
        t.setTransferPairId(null);
    }

    private Transaction owned(UUID id, UUID userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
        if (person != null) { // a person is never a merchant: drop the caller's own name and shared vote for it
            merchantService.forget(userId, rows.stream()
                    .map(r -> PayeeKey.of(r.getRawDescription(), r.getNormalizedDescription()))
                    .filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        return response(t, userId);
    }

    /**
     * Names the merchant of an unmatched payment: finds or creates the merchant, remembers it for the payee, and
     * applies it (dropping any person) to all of the caller's transactions with the same payee key. Whether the name
     * is shared follows the account setting and {@code share} (see {@link MerchantNameService#afterNaming}). Raw data
     * is untouched. A category the user set on this row moves to the merchant when they have none for it yet, so it
     * applies to all of the merchant's transactions.
     */
    @Transactional
    public TransactionResponse setMerchant(String email, UUID id, String name, Boolean share) {
        UUID userId = currentUser(email).getId();
        Transaction t = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String clean = PersonService.validName(name);
        String key = PayeeKey.of(t.getRawDescription(), t.getNormalizedDescription());
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction has no payee");
        }
        UUID merchantId = applyMerchant(userId, key, clean, share);
        if (t.getCategoryId() != null && categoryService.adoptMerchantCategory(userId, merchantId, t.getCategoryId(),
                "CREDIT".equals(t.getType()))) {
            t.setCategoryId(null);
            transactionRepository.save(t);
        }
        return response(t, userId);
    }

    /**
     * Renames one of the caller's merchant names: the payee moves to the (found or created) merchant with the new
     * name, as if named on a transaction, keeping the name's share opt-out. The user's category for the old merchant
     * carries over when they have none for the new one. Not the caller's name: 404.
     */
    @Transactional
    public void renameMerchant(String email, UUID aliasId, String name) {
        UUID userId = currentUser(email).getId();
        UserMerchantAlias alias = aliasRepository.findByUserIdAndPublicId(userId, aliasId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID previous = alias.getMerchantId();
        UUID merchantId = applyMerchant(userId, alias.getPayeeKey(), PersonService.validName(name), !alias.isShareOptOut());
        categoryService.merchantCategory(userId, previous).ifPresent(old -> {
            if (old.getCategoryId() != null) {
                categoryService.adoptMerchantCategory(userId, merchantId, old.getCategoryId(), false);
            }
            if (old.getCreditCategoryId() != null) {
                categoryService.adoptMerchantCategory(userId, merchantId, old.getCreditCategoryId(), true);
            }
        });
    }

    /** Points the payee at the merchant named {@code clean} for the user and all their payments to it; its id. */
    private UUID applyMerchant(UUID userId, String key, String clean, Boolean share) {
        Merchant merchant = merchantService.resolve(new MerchantMatch(clean, clean, null, null));
        UUID merchantId = merchant.getId();
        merchantService.saveAlias(userId, key, merchantId, clean);
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
        merchantNameService.afterNaming(userId, key, share);
        return merchantId;
    }

    /**
     * Reports the suggestion currently shown on one of the caller's own transactions as offensive or wrong. Not
     * yours, unknown, or showing no suggestion: 404.
     */
    @Transactional
    public void reportSuggestion(String email, UUID id) {
        UUID userId = currentUser(email).getId();
        Transaction t = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String key = t.getMerchantId() == null && shareable(t) ? PayeeKey.ofP2m(t.getRawDescription()).orElseThrow() : null;
        UUID merchantId = key == null ? null : merchantService.suggestions(userId, Set.of(key)).get(key);
        if (merchantId == null || merchantRepository.findById(merchantId)
                .filter(m -> MerchantService.nameReason(m.getCanonicalName()) == null).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        merchantNameService.report(userId, key, merchantId);
    }

    private TransactionResponse response(Transaction t, UUID userId) {
        return response(t, merchants(List.of(t)).get(t.getMerchantId()),
                ownMerchantNames(userId, List.of(t)).get(t.getId()), categoryResolver.forUser(userId),
                suggestions(userId, List.of(t)).get(t.getId()));
    }

    private static TransactionResponse response(Transaction t, Merchant merchant, String ownName,
                                                Resolution resolution, String suggested) {
        return TransactionResponse.from(t, merchant, ownName, resolution.person(t), resolution.resolve(t, merchant),
                suggested);
    }

    /** Whether the row may receive a shared suggestion; see {@link MerchantService#payeeReason}. */
    private static boolean shareable(Transaction t) {
        return MerchantService.payeeReason(t.getRawDescription(), t.getPersonId()) == null;
    }

    /**
     * The caller's own spelling by transaction id, for rows whose merchant came from the caller's alias for that
     * payee. One query per page.
     */
    private Map<UUID, String> ownMerchantNames(UUID userId, List<Transaction> transactions) {
        if (transactions.stream().allMatch(t -> t.getMerchantId() == null)) {
            return Map.of();
        }
        Map<String, UserMerchantAlias> aliases = merchantService.aliasEntries(userId);
        Map<UUID, String> out = new HashMap<>();
        for (Transaction t : transactions) {
            UserMerchantAlias alias = t.getMerchantId() == null ? null
                    : aliases.get(PayeeKey.of(t.getRawDescription(), t.getNormalizedDescription()));
            if (alias != null && alias.getMerchantId().equals(t.getMerchantId())) {
                out.put(t.getId(), alias.getDisplayName());
            }
        }
        return out;
    }

    /**
     * Suggested merchant names by transaction id, only for the given unmatched, shareable rows (no merchant; see
     * {@link #shareable}) whose payee key has a winning suggestion from at least two other users. Two queries per page.
     */
    private Map<UUID, String> suggestions(UUID userId, List<Transaction> transactions) {
        Map<UUID, String> keys = new HashMap<>();
        for (Transaction t : transactions) {
            if (t.getMerchantId() == null && shareable(t)) {
                keys.put(t.getId(), PayeeKey.ofP2m(t.getRawDescription()).orElseThrow());
            }
        }
        Map<String, UUID> winners = merchantService.suggestions(userId, Set.copyOf(keys.values()));
        if (winners.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = merchantRepository.findAllById(winners.values()).stream()
                .filter(m -> MerchantService.nameReason(m.getCanonicalName()) == null)
                .collect(Collectors.toMap(Merchant::getId, Merchant::getCanonicalName));
        Map<UUID, String> out = new HashMap<>();
        keys.forEach((id, key) -> {
            String name = names.get(winners.get(key));
            if (name != null) {
                out.put(id, name);
            }
        });
        return out;
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
     * merchant mapping (person and mapping: their money-in or money-out category by the row's type), else a merchant categoryHint naming one of the user's categories, else Uncategorized.
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
            Predicate credit = cb.equal(root.get("type"), "CREDIT");
            Predicate personValid = byDirection(cb, credit, root.get("personId"), f -> people(q, cb, userId, null, f));
            Predicate mappedValid = byDirection(cb, credit, root.get("merchantId"), f -> mappings(q, cb, userId, null, f));
            if (uncategorized) {
                return cb.and(cb.not(ownValid), cb.not(personValid), cb.not(mappedValid),
                        cb.not(in(cb, root.get("merchantId"), hinted(q, cb, userId, null))));
            }
            Predicate viaPerson = byDirection(cb, credit, root.get("personId"),
                    f -> people(q, cb, userId, categoryId, f));
            Predicate viaMapping = byDirection(cb, credit, root.get("merchantId"),
                    f -> mappings(q, cb, userId, categoryId, f));
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

    /** {@code column} IN the subquery for the row's direction: the creditCategoryId field for money in. */
    private static Predicate byDirection(CriteriaBuilder cb, Predicate credit, Path<UUID> column,
                                         Function<String, Subquery<UUID>> ids) {
        return cb.or(cb.and(credit, in(cb, column, ids.apply("creditCategoryId"))),
                cb.and(cb.not(credit), in(cb, column, ids.apply("categoryId"))));
    }

    /** The user's people whose {@code field} is {@code categoryId}, or any of the user's categories when null. */
    private static Subquery<UUID> people(AbstractQuery<?> q, CriteriaBuilder cb, UUID userId, UUID categoryId,
                                         String field) {
        Subquery<UUID> sq = q.subquery(UUID.class);
        Root<Person> p = sq.from(Person.class);
        return sq.select(p.get("id")).where(cb.equal(p.get("userId"), userId),
                categoryId == null ? p.<UUID>get(field).in(userCategories(sq, cb, userId))
                        : cb.equal(p.get(field), categoryId));
    }

    /** Merchants whose mapping {@code field} is {@code categoryId}, or any of the user's categories when null. */
    private static Subquery<UUID> mappings(AbstractQuery<?> q, CriteriaBuilder cb, UUID userId, UUID categoryId,
                                           String field) {
        Subquery<UUID> sq = q.subquery(UUID.class);
        Root<UserMerchantCategory> m = sq.from(UserMerchantCategory.class);
        return sq.select(m.get("merchantId")).where(cb.equal(m.get("userId"), userId),
                categoryId == null ? m.<UUID>get(field).in(userCategories(sq, cb, userId))
                        : cb.equal(m.get(field), categoryId));
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
