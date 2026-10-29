package com.expenseanalyzer.merchant.dto;

import jakarta.validation.constraints.NotNull;

public record AccountSharingRequest(@NotNull Boolean enabled) {
}
