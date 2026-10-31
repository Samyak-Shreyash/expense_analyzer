package com.expenseanalyzer.category.dto;

import java.util.UUID;

/**
 * Body for assigning a category to a merchant, person or transaction; a null categoryId clears the assignment.
 * {@code credit} (merchants and people only) picks the money-in default instead of the money-out one.
 */
public record CategoryAssignment(UUID categoryId, Boolean credit) {
}
