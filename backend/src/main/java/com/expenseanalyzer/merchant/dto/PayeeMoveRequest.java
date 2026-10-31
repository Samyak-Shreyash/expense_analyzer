package com.expenseanalyzer.merchant.dto;

import java.util.UUID;

/** Move one payee name of a person to another of the caller's people, or (personId null) to a new person of its own. */
public record PayeeMoveRequest(String payee, UUID personId) {
}
