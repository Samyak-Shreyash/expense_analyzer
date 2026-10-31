package com.expenseanalyzer.category.service;

import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.model.UserMerchantCategory;
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single definition of a transaction's effective category: its own category, else the person's category, else the
 * user's category for its merchant, else the user's category named like the merchant's categoryHint, else none.
 * {@link #forUser} loads the user's categories and merchant mappings once; resolve any number of rows from it.
 */
@Component
@RequiredArgsConstructor
public class EffectiveCategoryResolver {

    private final CategoryService categoryService;
    private final UserMerchantCategoryRepository mappingRepository;
    private final PersonRepository personRepository;

    /** Seeds the user's default categories if needed, so categoryHint matching works on first use. */
    @Transactional
    public Resolution forUser(UUID userId) {
        List<Category> categories = categoryService.ensureDefaults(userId);
        Map<UUID, UserMerchantCategory> byMerchant = mappingRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserMerchantCategory::getMerchantId, m -> m));
        Map<UUID, Person> people = personRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(Person::getId, p -> p));
        return new Resolution(categories, byMerchant, people);
    }

    public static final class Resolution {
        private final Map<UUID, Category> byId = new HashMap<>();
        private final Map<String, Category> byName = new HashMap<>();
        private final Map<UUID, UserMerchantCategory> merchantCategory;
        private final Map<UUID, Person> people;

        private Resolution(List<Category> categories, Map<UUID, UserMerchantCategory> merchantCategory,
                           Map<UUID, Person> people) {
            categories.forEach(c -> {
                byId.put(c.getId(), c);
                byName.put(key(c.getName()), c);
            });
            this.merchantCategory = merchantCategory;
            this.people = people;
        }

        /** @return the effective category, or null for Uncategorized. {@code merchant} may be null. */
        public Category resolve(Transaction t, Merchant merchant) {
            return resolve(t.getCategoryId(), t.getPersonId(), t.getMerchantId(), merchant, "CREDIT".equals(t.getType()));
        }

        /**
         * Same rule from the fields it uses, so aggregated groups can be resolved without loading transactions. The
         * person's and merchant's categories are their money-in ones for a {@code credit}, else their money-out ones.
         */
        public Category resolve(UUID categoryId, UUID personId, UUID merchantId, Merchant merchant, boolean credit) {
            if (categoryId != null && byId.containsKey(categoryId)) {
                return byId.get(categoryId);
            }
            // Keep in sync with TransactionService.categoryIs, which reimplements this rule in SQL.
            Person person = personId == null ? null : people.get(personId);
            UUID personCategory = person == null ? null : credit ? person.getCreditCategoryId() : person.getCategoryId();
            if (personCategory != null && byId.containsKey(personCategory)) {
                return byId.get(personCategory);
            }
            if (merchantId == null) {
                return null;
            }
            UserMerchantCategory mapping = merchantCategory.get(merchantId);
            UUID mapped = mapping == null ? null : mapping.categoryFor(credit);
            if (mapped != null && byId.containsKey(mapped)) {
                return byId.get(mapped);
            }
            return merchant == null || merchant.getCategoryHint() == null ? null : byName.get(key(merchant.getCategoryHint()));
        }

        /** The user's category with this name (case-insensitive), or null. */
        public Category named(String name) {
            return byName.get(key(name));
        }

        /** The transaction's person, or null. */
        public Person person(Transaction t) {
            return t.getPersonId() == null ? null : people.get(t.getPersonId());
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
