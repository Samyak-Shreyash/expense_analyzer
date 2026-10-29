package com.expenseanalyzer.category.dto;

import com.expenseanalyzer.category.model.Category;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, boolean countsAsSpending) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.isCountsAsSpending());
    }
}
