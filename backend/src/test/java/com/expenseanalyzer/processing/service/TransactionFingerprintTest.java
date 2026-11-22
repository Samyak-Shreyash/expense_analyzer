package com.expenseanalyzer.processing.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionFingerprintTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate DAY = LocalDate.of(2026, 1, 2);
    private static final BigDecimal PAISE = new BigDecimal("450");

    private static String fp(UUID user, LocalDate date, BigDecimal paise, String type, String desc, int occurrence) {
        return TransactionFingerprint.of(user, date, paise, type, desc, occurrence);
    }

    @Test
    void isStableHexSha256() {
        String a = fp(USER, DAY, PAISE, "DEBIT", "Coffee Shop", 0);
        assertThat(a).hasSize(64).matches("[0-9a-f]+");
        assertThat(fp(USER, DAY, PAISE, "DEBIT", "Coffee Shop", 0)).isEqualTo(a);
    }

    @Test
    void ignoresDescriptionCaseAndWhitespace() {
        assertThat(fp(USER, DAY, PAISE, "DEBIT", "  coffee   SHOP ", 0))
                .isEqualTo(fp(USER, DAY, PAISE, "DEBIT", "Coffee Shop", 0));
    }

    @Test
    void ignoresAmountScale() {
        assertThat(fp(USER, DAY, new BigDecimal("450.00"), "DEBIT", "Coffee", 0))
                .isEqualTo(fp(USER, DAY, PAISE, "DEBIT", "Coffee", 0));
    }

    @Test
    void repeatedIdenticalTransactionsAreDistinguishedByOccurrence() {
        assertThat(fp(USER, DAY, PAISE, "DEBIT", "Coffee", 1))
                .isNotEqualTo(fp(USER, DAY, PAISE, "DEBIT", "Coffee", 0));
    }

    @Test
    void refundOfSameAmountIsDifferentFromPurchase() {
        assertThat(fp(USER, DAY, PAISE, "CREDIT", "Coffee", 0))
                .isNotEqualTo(fp(USER, DAY, PAISE, "DEBIT", "Coffee", 0));
    }

    @Test
    void differsByDateAmountAndUser() {
        String base = fp(USER, DAY, PAISE, "DEBIT", "Coffee", 0);
        assertThat(fp(USER, DAY.plusDays(1), PAISE, "DEBIT", "Coffee", 0)).isNotEqualTo(base);
        assertThat(fp(USER, DAY, new BigDecimal("451"), "DEBIT", "Coffee", 0)).isNotEqualTo(base);
        assertThat(fp(UUID.randomUUID(), DAY, PAISE, "DEBIT", "Coffee", 0)).isNotEqualTo(base);
    }
}
