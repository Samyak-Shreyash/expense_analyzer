package com.expenseanalyzer.merchant.dto;

import jakarta.validation.constraints.NotNull;

public record AliasSharingRequest(@NotNull Boolean shared) {
}
