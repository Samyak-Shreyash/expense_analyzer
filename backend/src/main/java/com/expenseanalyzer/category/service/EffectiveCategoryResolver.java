package com.expenseanalyzer.category.service;

import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.model.UserMerchantCategory;
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.transaction.model.Transaction;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single definition of a transaction's effective category: its own category, else (person, added later), else the
 * user's category for its merchant, else the user's category named like the merchant's categoryHint, else none.
 * {@link #forUser} loads the user's categories and merchant mappings once; resolve any number of rows from it.
 */
@Component
@RequiredArgsConstructor
public class EffectiveCategoryResolver {

    private final CategoryService categoryService;
    private final UserMerchantCategoryRepository mappingRepository;

    /** Seeds the user's default categories if needed, so categoryHint matching works on first use. */
    @Transactional
    public Resolution forUser(UUID userId) {
        List<Category> categories = categoryService.ensureDefaults(userId);
        Map<UUID, UUID> byMerchant = mappingRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserMerchantCategory::getMerchantId, UserMerchantCategory::getCategoryId));
        return new Resolution(categories, byMerchant);
    }

    public static final class Resolution {
        private final Map<UUID, Category> byId = new HashMap<>();
        private final Map<String, Category> byName = new HashMap<>();
        private final Map<UUID, UUID> merchantCategory;

        private Resolution(List<Category> categories, Map<UUID, UUID> merchantCategory) {
            categories.forEach(c -> {
                byId.put(c.getId(), c);
                byName.put(key(c.getName()), c);
            });
            this.merchantCategory = merchantCategory;
        }

        /** @return the effective category, or null for Uncategorized. {@code merchant} may be null. */
        public Category resolve(Transaction t, Merchant merchant) {
            if (t.getCategoryId() != null && byId.containsKey(t.getCategoryId())) {
                return byId.get(t.getCategoryId());
            }
            // the person step goes here
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

        public Set<UUID> merchantsMappedTo(UUID categoryId) {
            return merchantCategory.entrySet().stream().filter(e -> e.getValue().equals(categoryId))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
        }

        public Set<UUID> mappedMerchants() {
            return merchantCategory.keySet();
        }

        /** Lower-cased names of all the user's categories, for matching merchant categoryHints. */
        public Set<String> lowerNames() {
            return byName.keySet();
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
