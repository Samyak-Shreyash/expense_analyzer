package com.expenseanalyzer.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PersonNameParserTest {

    /** Invented, bank-style samples; delimiter, token order and trailing fields differ per bank. */
    @ParameterizedTest
    @CsvSource(delimiter = '~', value = {
            // Axis
            "UPI/P2A/412345678901/RAHUL SHARMA/HDFC BANK/rent~Rahul Sharma",
            "UPI/P2P/503312345678/priya k nair/Axis Bank/gift~Priya K Nair",
            // HDFC
            "UPI-P2A-412345678902-ANITA DESAI-anita@okhdfcbank-HDFC0001234~Anita Desai",
            // SBI
            "TRANSFER TO UPI/DR/412345678903/P2A/MOHAMMED ALI/SBIN/mali@sbi~Mohammed Ali",
            "BY TRANSFER-UPI/CR/412345678904/P2P/SUNITA RAO/SBIN~Sunita Rao",
            // ICICI
            "UPI/412345678905/P2A/VIKRAM SINGH/ICIC0000123/UPI~Vikram Singh",
            "UPI/P2A/412345678906/ ANNA  D'SOUZA /ICICI Bank~Anna D'Souza",
            // a dash is part of the name when the description uses / or | delimiters
            "UPI/P2A/412345678907/MARY-ANNE JOSEPH/HDFC BANK~Mary-Anne Joseph",
            "UPI|P2A|412345678908|D'SOUTA-RAO|HDFC BANK~D'Souta-Rao"})
    void extractsTitleCasedName(String description, String expected) {
        assertThat(PersonNameParser.parse(description)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPI/P2M/412345678901/SWIGGY/YES BANK/order",
            "UPI/412345678901/RAHUL SHARMA/HDFC BANK",
            "UPI/P2A/RAHUL SHARMA/HDFC BANK",
            "UPI/P2A/412345678901",
            "UPI/P2A/412345678901/rahul@okaxis/AXIS BANK",
            "UPI/P2A/412345678901/9876543210/AXIS BANK",
            "POS 4521XXXX1234 SWIGGY BANGALORE",
            ""})
    void notAPerson(String description) {
        assertThat(PersonNameParser.parse(description)).isEqualTo(Optional.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPI/P2A/412345678901/9876543210/AXIS BANK",
            "UPI/P2A/412345678901/rahul@okaxis/AXIS BANK",
            "UPI-p2p-412345678901-RAHUL",
            "UPI/P2M/412345678901/ACME STORE/P2A/x"})
    void isTransferOnAnyP2aOrP2pToken(String description) {
        assertThat(PersonNameParser.isTransfer(description)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPI/P2M/412345678901/SWIGGY/YES BANK/order", "NEFT/N1234/XP2AB/HDFC", ""})
    void notATransferWithoutTheToken(String description) {
        assertThat(PersonNameParser.isTransfer(description)).isFalse();
    }
}
