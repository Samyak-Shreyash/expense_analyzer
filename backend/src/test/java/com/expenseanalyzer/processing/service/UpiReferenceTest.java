package com.expenseanalyzer.processing.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpiReferenceTest {

    @Test
    void googlePayRow() {
        String input = "Paid to Blinkit | UPI Transaction ID: 612148248644 | Paid by Axis Bank 6457";
        assertThat(UpiReference.find(input)).isEqualTo("612148248644");
    }

    @Test
    void bankSlashFormat() {
        String input = "UPI/DR/412345678903/P2A/MOHAMMED ALI/SBIN/ali@okaxis";
        assertThat(UpiReference.find(input)).isEqualTo("412345678903");
    }

    @Test
    void bankDashFormat() {
        String input = "UPI-RAMESH KUMAR-ramesh@oksbi-SBIN0001234-412345678901-dinner";
        assertThat(UpiReference.find(input)).isEqualTo("412345678901");
    }

    @Test
    void noUpiWordReturnsNull() {
        String input = "NEFT 412345678901 SALARY";
        assertThat(UpiReference.find(input)).isNull();
    }

    @Test
    void twoCandidatesReturnsNull() {
        String input = "UPI/412345678901/412345678902/SHOP";
        assertThat(UpiReference.find(input)).isNull();
    }

    @Test
    void longerNumberIsNotAReference() {
        String input = "UPI/1234567890123/SHOP";
        assertThat(UpiReference.find(input)).isNull();
    }

    @Test
    void phoneNumberIsNotAReference() {
        String input = "UPI/9876543210@ybl/RAMESH";
        assertThat(UpiReference.find(input)).isNull();
    }

}
