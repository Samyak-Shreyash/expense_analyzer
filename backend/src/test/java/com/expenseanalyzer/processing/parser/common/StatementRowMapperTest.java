package com.expenseanalyzer.processing.parser.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementRowMapperTest {

    private final StatementRowMapper dc =
            new StatementRowMapper(List.of("Date", "Description", "Debit", "Credit"));
    private final StatementRowMapper amt =
            new StatementRowMapper(List.of("Date", "Description", "Amount"));

    private static ParsedTransaction tx(Object o) {
        assertThat(o).isInstanceOf(ParsedTransaction.class);
        return (ParsedTransaction) o;
    }

    private static void err(Object o, RowErrorCode code) {
        assertThat(o).isInstanceOf(RowError.class);
        assertThat(((RowError) o).code()).isEqualTo(code);
    }

    @Test
    void headerValidation() {
        assertThat(new StatementRowMapper(List.of(" date ", "DESCRIPTION", "Debit", "Credit")).hasValidHeaders()).isTrue();
        assertThat(new StatementRowMapper(List.of("Date", "Description")).hasValidHeaders()).isFalse();
    }

    @Test
    void debitWithThousandsSeparator() {
        ParsedTransaction t = tx(dc.map(2, List.of("2025-12-31", "x", "1,250.50", "")));
        assertThat(t.type()).isEqualTo("DEBIT");
        assertThat(t.amount()).isEqualTo(new BigDecimal("1250.50"));
        assertThat(t.rowNumber()).isEqualTo(2);
    }

    @Test
    void credit() {
        ParsedTransaction t = tx(dc.map(2, List.of("2025-12-31", "x", "", "100")));
        assertThat(t.type()).isEqualTo("CREDIT");
        assertThat(t.amount()).isEqualTo(new BigDecimal("100"));
    }

    @Test
    void singleAmountSign() {
        ParsedTransaction d = tx(amt.map(2, List.of("2025-12-31", "x", "-45.10")));
        assertThat(d.type()).isEqualTo("DEBIT");
        assertThat(d.amount()).isEqualTo(new BigDecimal("45.10"));
        assertThat(tx(amt.map(3, List.of("2025-12-31", "x", "45.10"))).type()).isEqualTo("CREDIT");
    }

    @Test
    void dates() {
        for (String d : List.of("31/12/2025", "31-12-2025", "2025-12-31")) {
            assertThat(tx(dc.map(2, List.of(d, "x", "1", ""))).date()).isEqualTo(LocalDate.of(2025, 12, 31));
        }
        err(dc.map(2, List.of("12/31/2025", "x", "1", "")), RowErrorCode.INVALID_DATE);
        err(dc.map(2, List.of("2025-13-01", "x", "1", "")), RowErrorCode.INVALID_DATE);
    }

    @Test
    void ambiguousDirection() {
        err(dc.map(2, List.of("2025-12-31", "x", "10", "10")), RowErrorCode.AMBIGUOUS_DIRECTION);
        err(dc.map(2, List.of("2025-12-31", "x", "", "")), RowErrorCode.AMBIGUOUS_DIRECTION);
    }

    @Test
    void zeroInUnusedColumnIsIgnored() {
        ParsedTransaction d = tx(dc.map(2, List.of("2025-12-31", "x", "12.50", "0.00")));
        assertThat(d.type()).isEqualTo("DEBIT");
        assertThat(d.amount()).isEqualTo(new BigDecimal("12.50"));
        assertThat(tx(dc.map(3, List.of("2025-12-31", "x", "0", "7"))).type()).isEqualTo("CREDIT");
        err(dc.map(4, List.of("2025-12-31", "x", "0.00", "0")), RowErrorCode.AMBIGUOUS_DIRECTION);
    }

    @Test
    void invalidAmounts() {
        for (String a : List.of("abc", "$5", "0", "1e5", "9E999999999", "1,2,3", "+5", "1.234", "12345678901")) {
            err(amt.map(2, List.of("2025-12-31", "x", a)), RowErrorCode.INVALID_AMOUNT);
        }
    }

    @Test
    void groupedAmountIsValid() {
        assertThat(amt.map(2, List.of("2025-12-31", "x", "1,234.56")))
                .isInstanceOfSatisfying(ParsedTransaction.class,
                        p -> assertThat(p.amount()).isEqualByComparingTo("1234.56"));
    }

    @Test
    void indianLakhGroupingIsValid() {
        assertThat(tx(amt.map(2, List.of("2025-12-31", "x", "1,23,456.78"))).amount()).isEqualByComparingTo("123456.78");
        assertThat(tx(amt.map(2, List.of("2025-12-31", "x", "-12,34,56,789"))).amount()).isEqualByComparingTo("123456789");
        assertThat(tx(dc.map(2, List.of("2025-12-31", "x", "85,000.00", ""))).amount()).isEqualByComparingTo("85000.00");
    }

    @Test
    void malformedGroupingIsInvalid() {
        for (String a : List.of("1,2345.00", "12,34", ",123", "123,", "1,23,4567")) {
            err(amt.map(2, List.of("2025-12-31", "x", a)), RowErrorCode.INVALID_AMOUNT);
        }
    }

    @Test
    void descriptionRules() {
        err(dc.map(2, List.of("2025-12-31", "  ", "1", "")), RowErrorCode.MISSING_DESCRIPTION);
        err(dc.map(2, List.of("2025-12-31", "a".repeat(2049), "1", "")), RowErrorCode.DESCRIPTION_TOO_LONG);
        assertThat(tx(dc.map(2, List.of("2025-12-31", "  AMZN*MKTP  IN ", "1", ""))).rawDescription())
                .isEqualTo("AMZN*MKTP  IN");
    }

    @Test
    void shortRowTreatedAsBlank() {
        err(dc.map(2, List.of("2025-12-31", "x")), RowErrorCode.AMBIGUOUS_DIRECTION);
    }
}
