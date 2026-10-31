package com.expenseanalyzer.merchant.dto;

import com.expenseanalyzer.merchant.model.Person;
import java.util.List;
import java.util.UUID;

/**
 * {@code categoryId} / {@code creditCategoryId} are the money-out / money-in defaults; {@code payees} are the UPI payee
 * names grouped under this person (upper-cased, as matched).
 */
public record PersonResponse(UUID id, String name, UUID categoryId, UUID creditCategoryId, List<String> payees) {

    public static PersonResponse from(Person p, List<String> payees) {
        return new PersonResponse(p.getId(), p.getName(), p.getCategoryId(), p.getCreditCategoryId(), payees);
    }
}
