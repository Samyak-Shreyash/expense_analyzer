package com.expenseanalyzer.processing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountDetectorTest {

    @Test
    void findsLastFourDigitsFromFullAccountNumber() {
        String text = "Account No : 50100123456789\nStatement period";
        assertThat(AccountDetector.find(text)).isEqualTo(Optional.of("6789"));
    }

    @Test
    void findsLastFourDigitsFromMaskedAccountNumber() {
        String text = "A/c No. XXXXXX4321";
        assertThat(AccountDetector.find(text)).isEqualTo(Optional.of("4321"));
    }

    @Test
    void findsLastFourDigitsFromSpaceSeparatedAccountNumber() {
        String text = "Acct # 1234 5678 9012";
        assertThat(AccountDetector.find(text)).isEqualTo(Optional.of("9012"));
    }

    @Test
    void returnsEmptyWhenNoAccountFound() {
        String text = "Date Narration Amount";
        assertThat(AccountDetector.find(text)).isEqualTo(Optional.empty());
    }

    @Test
    void ignoresAccountAfterFirstTwoThousandCharacters() {
        String text = "x".repeat(2100) + " Account No: 123456789";
        assertThat(AccountDetector.find(text)).isEqualTo(Optional.empty());
    }
}
