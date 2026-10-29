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
        Map<UUID, UUID> byMerchant = mappingRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserMerchantCategory::getMerchantId, UserMerchantCategory::getCategoryId));
        Map<UUID, Person> people = personRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(Person::getId, p -> p));
        return new Resolution(categories, byMerchant, people);
    }

    public static final class Resolution {
        private final Map<UUID, Category> byId = new HashMap<>();
        private final Map<String, Category> byName = new HashMap<>();
        private final Map<UUID, UUID> merchantCategory;
        private final Map<UUID, Person> people;

        private Resolution(List<Category> categories, Map<UUID, UUID> merchantCategory, Map<UUID, Person> people) {
            categories.forEach(c -> {
                byId.put(c.getId(), c);
                byName.put(key(c.getName()), c);
            });
            this.merchantCategory = merchantCategory;
            this.people = people;
        }

        /** @return the effective category, or null for Uncategorized. {@code merchant} may be null. */
        public Category resolve(Transaction t, Merchant merchant) {
            if (t.getCategoryId() != null && byId.containsKey(t.getCategoryId())) {
                return byId.get(t.getCategoryId());
            }
            // Keep in sync with TransactionService.categoryIs, which reimplements this rule in SQL.
            Person person = person(t);
            if (person != null && person.getCategoryId() != null && byId.containsKey(person.getCategoryId())) {
                return byId.get(person.getCategoryId());
            }
            if (t.getMerchantId() == null) {
                return null;
            }
            UUID mapped = merchantCategory.get(t.getMerchantId());
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
