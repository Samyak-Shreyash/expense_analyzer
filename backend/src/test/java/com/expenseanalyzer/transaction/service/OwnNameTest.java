package com.expenseanalyzer.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OwnNameTest {

    private static final String NAME = "Samyak Shreyash";

    @ParameterizedTest
    @ValueSource(strings = {"SAMYAK SHREYASH UTIB", "SAMYAKSH HDFCB ANK", "UPI/123456789012/Samyaksh/x@okaxis",
            "P2M SAMYAK SHREYASH PAID V", "NEFT-EMI SAMYAK SHREYASH"})
    void matchesOwnName(String text) {
        assertThat(OwnName.mentions(NAME, text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAMYAK JAIN", "ZOMATO", "SHREYASH KUMAR"})
    void ignoresOtherText(String text) {
        assertThat(OwnName.mentions(NAME, text)).isFalse();
    }

    @Test
    void blankOrShortNamesNeverMatch() {
        assertThat(OwnName.mentions(null, "SAMYAK SHREYASH")).isFalse();
        assertThat(OwnName.mentions("", "SAMYAK SHREYASH")).isFalse();
        assertThat(OwnName.mentions("Al Bo", "AL BO")).isFalse();
    }
}
