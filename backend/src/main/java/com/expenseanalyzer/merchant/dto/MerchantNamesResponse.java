package com.expenseanalyzer.merchant.dto;

import java.util.List;

public record MerchantNamesResponse(boolean shareMerchantNames, boolean sharingRevoked, List<MerchantNameItem> items) {
}
