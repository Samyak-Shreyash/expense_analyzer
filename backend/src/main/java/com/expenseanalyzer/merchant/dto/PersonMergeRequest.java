package com.expenseanalyzer.merchant.dto;

import java.util.UUID;

/** Merge a person into another of the caller's people. */
public record PersonMergeRequest(UUID intoId) {
}
