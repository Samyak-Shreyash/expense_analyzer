package com.expenseanalyzer.user.dto;

import com.expenseanalyzer.user.model.UserAccount;
import java.util.UUID;

/** The number is masked: the user typed it and does not need it back. */
public record UserAccountResponse(UUID id, String accountNumber, String bankName) {

    public static UserAccountResponse from(UserAccount a) {
        return new UserAccountResponse(a.getId(), UserAccount.mask(a.getAccountNumber()), a.getBankName());
    }
}
