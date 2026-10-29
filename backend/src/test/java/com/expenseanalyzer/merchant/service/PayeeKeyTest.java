package com.expenseanalyzer.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PayeeKeyTest {

    /** Invented, bank-style P2M samples; the key is the payee segment whatever follows it. */
    @ParameterizedTest
    @CsvSource(delimiter = '~', value = {
            "UPI/P2M/412345678902/ACME STORE/HDFC/pay~ACME STORE",
            "UPI/P2M/412345678902/ acme   store /HDFC/different remark~ACME STORE",
            "UPI-P2M-412345678903-Acme Store-acme@okhdfcbank-HDFC0001234~ACME STORE",
            "UPI/412345678905/P2M/Acme Store/ICIC0000123/UPI~ACME STORE",
            "TRANSFER TO UPI/DR/412345678904/P2M/ACME STORE/SBIN/x~ACME STORE",
            // a dash in a merchant segment still ends the key (only person names keep their dashes)
            "UPI/P2M/412345678901/SWIGGY-FOOD/YES BANK/x~SWIGGY",
            "UPI-P2M-412345678901-SWIGGY-FOOD-YES BANK-x~SWIGGY"})
    void p2mUsesPayeeSegment(String description, String expected) {
        assertThat(PayeeKey.of(description, "IGNORED")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '~', value = {
            "POS 4521XXXX1234 CORNER SHOP~corner  shop~CORNER SHOP",
            "UPI/P2A/412345678901/RAHUL SHARMA/HDFC~p2a rahul sharma~P2A RAHUL SHARMA",
            "UPI/P2M/not-a-reference~ upi shop ~UPI SHOP"})
    void otherwiseUsesNormalizedDescription(String raw, String normalized, String expected) {
        assertThat(PayeeKey.of(raw, normalized)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL", "'  ',NULL"}, nullValues = "NULL")
    void nullWhenNothingToKeyOn(String raw, String normalized) {
        assertThat(PayeeKey.of(raw, normalized)).isNull();
    }

    @Test
    void ofP2mOnlyForMerchantPayeeSegments() {
        assertThat(PayeeKey.ofP2m("UPI/P2M/412345678902/ACME STORE/HDFC/pay")).contains("ACME STORE");
        assertThat(PayeeKey.ofP2m("UPI/P2A/412345678901/RAHUL SHARMA/HDFC")).isEmpty();
        assertThat(PayeeKey.ofP2m("NEFT/N123456789012/RAHUL SHARMA/HDFC")).isEmpty();
        assertThat(PayeeKey.ofP2m(null)).isEmpty();
    }
}
