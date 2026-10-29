package com.expenseanalyzer.category.dto;

import java.util.UUID;

/** Body for assigning a category to a merchant or transaction; a null categoryId clears the assignment. */
public record CategoryAssignment(UUID categoryId) {
}
