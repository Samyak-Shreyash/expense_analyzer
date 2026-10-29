package com.expenseanalyzer.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.category.dto.CategoryRequest;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.repository.CategoryRepository;
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A concurrent create/rename that loses the unique-index race is a 409, not a 500. */
class CategoryServiceConflictTest {

    private static final String EMAIL = "alice@example.com";

    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UUID userId = UUID.randomUUID();
    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, mock(TransactionRepository.class), userRepository,
                mock(UserMerchantCategoryRepository.class), mock(MerchantRepository.class), mock(PersonRepository.class));
        User u = new User();
        u.setId(userId);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
        when(categoryRepository.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of()); // the pre-check passes
        when(categoryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_categories"));
    }

    @Test
    void createLosingTheRaceIsConflictWithTheDuplicateMessage() {
        assertThatThrownBy(() -> service.create(EMAIL, new CategoryRequest("Pets", true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("Category name already exists");
                });
    }

    @Test
    void renameLosingTheRaceIsConflictWithTheDuplicateMessage() {
        Category own = new Category(userId, "Gifts", true);
        UUID id = UUID.randomUUID();
        when(categoryRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(own));
        own.setId(id);

        assertThatThrownBy(() -> service.update(EMAIL, id, new CategoryRequest("Pets", true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("Category name already exists");
                });
    }
}
