package com.expenseanalyzer.processing.parser.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvStatementParserTest {

    private final CsvStatementParser parser = new CsvStatementParser();

    private ParsedStatement parse(String csv) {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesDebitCreditColumns() {
        ParsedStatement r = parse("Date,Description,Debit,Credit\n2025-01-05,Coffee Shop,4.50,\n2025-01-06,Salary,,3000.00\n");
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).hasSize(2);
        assertThat(r.transactions().get(0).type()).isEqualTo("DEBIT");
        assertThat(r.transactions().get(0).amount()).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(r.transactions().get(1).type()).isEqualTo("CREDIT");
        assertThat(r.transactions().get(1).amount()).isEqualByComparingTo(new BigDecimal("3000.00"));
    }

    @Test
    void parsesSingleAmountColumn() {
        ParsedStatement r = parse("Date,Description,Amount\n2025-01-05,Coffee,-12.00\n2025-01-06,Refund,12.00\n");
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions().get(0).type()).isEqualTo("DEBIT");
        assertThat(r.transactions().get(1).type()).isEqualTo("CREDIT");
        assertThat(r.transactions().get(0).amount()).isEqualByComparingTo("12.00");
    }

    @Test
    void headersCaseInsensitiveAnyOrder() {
        ParsedStatement r = parse("amount,DESCRIPTION,date,Balance\n-5.00,Tea,2025-02-01,100\n");
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).hasSize(1);
        assertThat(r.transactions().get(0).rawDescription()).isEqualTo("Tea");
    }

    @Test
    void handlesQuotedFieldsWithCommasAndNewlines() {
        ParsedStatement r = parse("Date,Description,Amount\n2025-01-05,\"ACME, Inc.\nPayment\",-9.99\n");
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions().get(0).rawDescription()).isEqualTo("ACME, Inc.\nPayment");
    }

    @Test
    void reportsInvalidRowsAndKeepsValidOnes() {
        ParsedStatement r = parse("Date,Description,Amount\n"
                + "2025-01-01,A,-1.00\n"
                + "not-a-date,B,-2.00\n"
                + "\n"
                + "2025-01-03,C,-3.00\n"
                + "2025-01-04,D,abc\n"
                + "2025-01-05,E,-5.00\n");
        assertThat(r.transactions()).hasSize(3);
        assertThat(r.errors()).extracting(RowError::rowNumber, RowError::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3, RowErrorCode.INVALID_DATE),
                        org.assertj.core.groups.Tuple.tuple(5, RowErrorCode.INVALID_AMOUNT));
    }

    @Test
    void missingRequiredHeaderReturnsErrorNotException() {
        ParsedStatement r = parse("Foo,Bar\n1,2\n");
        assertThat(r.transactions()).isEmpty();
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.MISSING_HEADER);
    }

    @Test
    void emptyInputReturnsMissingHeader() {
        ParsedStatement r = parse("");
        assertThat(r.transactions()).isEmpty();
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.MISSING_HEADER);
    }

    @Test
    void errorsDoNotContainCellValues() {
        ParsedStatement r = parse("Date,Description,Amount\n2025-01-01,X,SECRET123\n");
        assertThat(r.errors()).hasSize(1);
        assertThat(r.errors().toString()).doesNotContain("SECRET123");
    }

    @Test
    void stripsUtf8Bom() {
        ParsedStatement r = parse("\uFEFFDate,Description,Amount\n2025-01-01,X,-1.00\n");
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).hasSize(1);
    }
}
