package com.expenseanalyzer.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
    @NotBlank(message = "Password is required")
    String password
) {

    @Override
    public String toString() {
        return "DeleteAccountRequest[password=***]";
    }
}
