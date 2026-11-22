package com.expenseanalyzer.category.service;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordCategoryTest {

    @Test
    void emiTokenIsLoanEmi() {
        assertThat(KeywordCategory.of("NEFT-EMI SAMYAK SHREYASH")).contains("Loan EMI");
    }

    @Test
    void rentTokenIsRent() {
        assertThat(KeywordCategory.of("UPI/P2A/123456789012/DUMMY NAME RENT")).contains("Rent");
    }

    @Test
    void onlyWholeTokensMatch() {
        assertThat(KeywordCategory.of("EMIRATES")).isEqualTo(Optional.empty());
        assertThat(KeywordCategory.of("PARENT")).isEqualTo(Optional.empty());
    }

    @Test
    void healthAndInsuranceKeywords() {
        assertThat(KeywordCategory.of("APOLLO PHARMACY")).contains("Health");
        assertThat(KeywordCategory.of("lic of india premium")).contains("Insurance");
    }

    @Test
    void repeatedTokensAreFine() {
        assertThat(KeywordCategory.of("RENT RENT")).contains("Rent");
    }

    @Test
    void firstMatchInOrderWins() {
        assertThat(KeywordCategory.of("EMI RENT")).contains("Loan EMI");
    }
}
