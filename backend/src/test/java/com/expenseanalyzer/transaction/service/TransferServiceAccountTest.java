package com.expenseanalyzer.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransferServiceAccountTest {

    @Test
    void detectsFullAccountNumberInDescription() {
        assertThat(TransferService.mentionsAccount("NEFT TO XX6789 SELF", "50100123456789")).isTrue();
    }

    @Test
    void detectsAccountNumberInUPIReference() {
        assertThat(TransferService.mentionsAccount("IMPS/50100123456789/SAVINGS", "50100123456789")).isTrue();
    }

    @Test
    void ignoresOtherAccountNumberInDescription() {
        assertThat(TransferService.mentionsAccount("UPI/P2A/663995116789/SHOP", "50100123456789")).isFalse();
    }

    @Test
    void ignoresMaskedAccountNumberInDescription() {
        assertThat(TransferService.mentionsAccount("ATM WDL 6789", "50100123456789")).isFalse();
    }

    @Test
    void returnsFalseWhenDescriptionIsNull() {
        assertThat(TransferService.mentionsAccount(null, "50100123456789")).isFalse();
    }

    @Test
    void detectsMaskedAccountNumberWithPartialMatch() {
        assertThat(TransferService.mentionsAccount("TRF TO **6789", "XXXX6789")).isTrue();
    }
}
