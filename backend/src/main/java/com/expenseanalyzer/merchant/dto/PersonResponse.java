package com.expenseanalyzer.merchant.dto;

import com.expenseanalyzer.merchant.model.Person;
import java.util.UUID;

public record PersonResponse(UUID id, String name, UUID categoryId) {

    public static PersonResponse from(Person p) {
        return new PersonResponse(p.getId(), p.getName(), p.getCategoryId());
    }
}
