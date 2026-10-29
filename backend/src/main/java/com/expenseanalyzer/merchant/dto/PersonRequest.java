package com.expenseanalyzer.merchant.dto;

import java.util.UUID;

/** Rename a person and/or set their default category (null clears it). */
public record PersonRequest(String name, UUID categoryId) {
}
