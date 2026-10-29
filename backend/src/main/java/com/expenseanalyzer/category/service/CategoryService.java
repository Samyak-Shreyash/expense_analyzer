package com.expenseanalyzer.category.service;

import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import com.expenseanalyzer.category.dto.CategoryRequest;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.model.UserMerchantCategory;
import com.expenseanalyzer.category.repository.CategoryRepository;
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.service.MerchantNormalizer;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owner-scoped category management. Another user's category is indistinguishable from a missing one (404). */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final List<String> NON_SPENDING_DEFAULTS = List.of("Repayment", "Loan");

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final UserMerchantCategoryRepository merchantCategoryRepository;
    private final MerchantRepository merchantRepository;

    public UUID userId(String email) {
        return userRepository.findByEmail(email).map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    /** Seeds the default categories the user does not have yet (matched case-insensitively); safe to call repeatedly. */
    @Transactional
    public List<Category> ensureDefaults(UUID userId) {
        List<Category> existing = categoryRepository.findByUserIdOrderByNameAsc(userId);
        Set<String> have = existing.stream().map(c -> key(c.getName())).collect(Collectors.toSet());
        boolean added = false;
        for (String name : MerchantNormalizer.defaultCategoryNames()) {
            added |= seed(userId, name, true, have);
        }
        for (String name : NON_SPENDING_DEFAULTS) {
            added |= seed(userId, name, false, have);
        }
        return added ? categoryRepository.findByUserIdOrderByNameAsc(userId) : existing;
    }

    @Transactional
    public List<Category> list(String email) {
        return ensureDefaults(userId(email));
    }

    @Transactional
    public Category create(String email, CategoryRequest request) {
        UUID userId = userId(email);
        String name = validName(request);
        ensureUnique(userId, name, null);
        return categoryRepository.save(new Category(userId, name, spends(request)));
    }

    @Transactional
    public Category update(String email, UUID id, CategoryRequest request) {
        UUID userId = userId(email);
        Category category = owned(userId, id);
        String name = validName(request);
        ensureUnique(userId, name, id);
        category.setName(name);
        category.setCountsAsSpending(spends(request));
        category.setUpdatedAt(Instant.now());
        return category;
    }

    /** Clears every reference to the category, then deletes it. Task 3 adds its reference table here. */
    @Transactional
    public void delete(String email, UUID id) {
        Category category = owned(userId(email), id);
        transactionRepository.clearCategory(id);
        merchantCategoryRepository.deleteByCategoryId(id);
        categoryRepository.delete(category);
    }

    private boolean seed(UUID userId, String name, boolean spends, Set<String> have) {
        if (!have.add(key(name))) {
            return false;
        }
        categoryRepository.save(new Category(userId, name, spends));
        return true;
    }

    /** Sets (or, with a null categoryId, clears) the caller's category for a merchant. Unknown merchant or category: 404. */
    @Transactional
    public void setMerchantCategory(String email, UUID merchantId, UUID categoryId) {
        UUID userId = userId(email);
        if (!merchantRepository.existsById(merchantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (categoryId == null) {
            merchantCategoryRepository.findByUserIdAndMerchantId(userId, merchantId)
                    .ifPresent(merchantCategoryRepository::delete);
            return;
        }
        owned(userId, categoryId);
        merchantCategoryRepository.save(new UserMerchantCategory(userId, merchantId, categoryId));
    }

    public Category owned(UUID userId, UUID id) {
        return categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void ensureUnique(UUID userId, String name, UUID selfId) {
        boolean taken = categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .anyMatch(c -> !c.getId().equals(selfId) && key(c.getName()).equals(key(name)));
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category name already exists");
        }
    }

    private static String validName(CategoryRequest request) {
        String name = request == null || request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be 1-" + MAX_NAME_LENGTH + " characters");
        }
        if (key(name).equals(key(SpendingAnalyticsService.UNCATEGORIZED))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is reserved");
        }
        return name;
    }

    private static boolean spends(CategoryRequest request) {
        return request.countsAsSpending() == null || request.countsAsSpending();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
