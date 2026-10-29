package com.expenseanalyzer.merchant.service;

/** Why a merchant name can never be shared. Ordered: payee reasons first, then name reasons. */
public enum NotShareableReason {
    TRANSFER, NOT_UPI_MERCHANT, NAME_NOT_ALLOWED, NAME_OFFENSIVE
}
