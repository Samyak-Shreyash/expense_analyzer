package com.expenseanalyzer.processing.parser.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelStatementParserTest {

    private final ExcelStatementParser parser = new ExcelStatementParser();

    private static void fill(Sheet s, Object[][] rows, CellStyle dateStyle) {
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == null) {
                continue; // leave row absent
            }
            Row row = s.createRow(i);
            for (int j = 0; j < rows[i].length; j++) {
                Object v = rows[i][j];
                if (v instanceof String str) {
                    row.createCell(j).setCellValue(str);
                } else if (v instanceof Double d) {
                    row.createCell(j).setCellValue(d);
                } else if (v instanceof LocalDate ld) {
                    Cell c = row.createCell(j);
                    c.setCellValue(ld);
                    c.setCellStyle(dateStyle);
                }
            }
        }
    }

    private static byte[] workbook(Object[][]... sheets) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle ds = wb.createCellStyle();
            ds.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            for (int i = 0; i < sheets.length; i++) {
                fill(wb.createSheet("S" + i), sheets[i], ds);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private ParsedStatement parse(byte[] b) {
        return parser.parse(new ByteArrayInputStream(b));
    }

    @Test
    void parsesDebitCreditSheet() throws IOException {
        ParsedStatement r = parse(workbook(new Object[][] {
            {"Date", "Description", "Debit", "Credit"},
            {"05/01/2025", "Coffee", "4.50", ""},
            {"06/01/2025", "Salary", "", "3000.00"}}));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).hasSize(2);
        assertThat(r.transactions().get(0).type()).isEqualTo("DEBIT");
        assertThat(r.transactions().get(1).type()).isEqualTo("CREDIT");
        assertThat(r.transactions().get(0).date()).isEqualTo(LocalDate.of(2025, 1, 5));
    }

    @Test
    void parsesDateFormattedCellsAndNumericAmounts() throws IOException {
        ParsedStatement r = parse(workbook(new Object[][] {
            {"Date", "Description", "Debit", "Credit"},
            {LocalDate.of(2025, 1, 5), "Rent", 1234.5, null}}));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).hasSize(1);
        assertThat(r.transactions().get(0).amount()).isEqualByComparingTo("1234.5");
        assertThat(r.transactions().get(0).type()).isEqualTo("DEBIT");
        assertThat(r.transactions().get(0).date()).isEqualTo(LocalDate.of(2025, 1, 5));
    }

    @Test
    void numericZeroInUnusedColumnAndWholeNumbersMatchCsvScale() throws IOException {
        ParsedStatement r = parse(workbook(new Object[][] {
            {"Date", "Description", "Debit", "Credit"},
            {"05/01/2025", 12345.0, 1001.0, 0.0}}));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).hasSize(1);
        assertThat(r.transactions().get(0).type()).isEqualTo("DEBIT");
        assertThat(r.transactions().get(0).amount()).isEqualTo(new java.math.BigDecimal("1001"));
        assertThat(r.transactions().get(0).rawDescription()).isEqualTo("12345");
    }

    @Test
    void reportsInvalidRowsAndKeepsValidOnes() throws IOException {
        ParsedStatement r = parse(workbook(new Object[][] {
            {"Date", "Description", "Amount"},
            {"05/01/2025", "Ok", "-5.00"},
            {"nope", "BadDate", "-5.00"},
            {"06/01/2025", "BadAmt", "abc"}}));
        assertThat(r.transactions()).hasSize(1);
        assertThat(r.errors()).extracting(RowError::rowNumber, RowError::code).containsExactly(
            tuple(3, RowErrorCode.INVALID_DATE),
            tuple(4, RowErrorCode.INVALID_AMOUNT));
    }

    @Test
    void skipsBlankRows() throws IOException {
        ParsedStatement r = parse(workbook(new Object[][] {
            {"Date", "Description", "Amount"},
            null,
            {"", "", ""},
            {"05/01/2025", "Ok", "-5.00"}}));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).hasSize(1);
        assertThat(r.transactions().get(0).rowNumber()).isEqualTo(4);
    }

    @Test
    void missingRequiredHeaderReturnsError() throws IOException {
        ParsedStatement r = parse(workbook(new Object[][] {{"Foo", "Bar"}, {"a", "b"}}));
        assertThat(r.transactions()).isEmpty();
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.MISSING_HEADER);
    }

    @Test
    void corruptFileReturnsUnreadableError() {
        ParsedStatement r = parse("not a zip".getBytes());
        assertThat(r.transactions()).isEmpty();
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.UNREADABLE_FILE);
    }

    @Test
    void readsFirstSheetOnly() throws IOException {
        ParsedStatement r = parse(workbook(
            new Object[][] {{"Date", "Description", "Amount"}, {"05/01/2025", "One", "-1.00"}},
            new Object[][] {{"Date", "Description", "Amount"}, {"06/01/2025", "Two", "-2.00"}}));
        assertThat(r.transactions()).hasSize(1);
        assertThat(r.transactions().get(0).rawDescription()).isEqualTo("One");
    }

    // ---- resource limits (SEC-016) ----

    private static Object[][] rows(int n) {
        Object[][] rows = new Object[n + 1][];
        rows[0] = new Object[] {"Date", "Description", "Amount"};
        for (int i = 1; i <= n; i++) {
            rows[i] = new Object[] {"05/01/2025", "A", "-1"};
        }
        return rows;
    }

    @Test
    void tooManyRowsFailsTheWholeWorkbook() throws IOException {
        ParsedStatement r = parser.parse(new ByteArrayInputStream(workbook(rows(4))),
                new ParserLimits(3, 50, Duration.ofSeconds(30)));
        assertThat(r.transactions()).isEmpty();
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.TOO_MANY_ROWS);
    }

    @Test
    void rowCountAtTheLimitIsAcceptedExcludingHeader() throws IOException {
        ParsedStatement r = parser.parse(new ByteArrayInputStream(workbook(rows(3))),
                new ParserLimits(3, 50, Duration.ofSeconds(30)));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).hasSize(3);
    }

    @Test
    void tooManyColumnsFailsTheWholeWorkbook() throws IOException {
        Object[][] wide = {{"Date", "Description", "Amount", "a", "b", "c", "d"}, {"05/01/2025", "A", "-1", "", "", "", "x"}};
        ParsedStatement r = parser.parse(new ByteArrayInputStream(workbook(wide)),
                new ParserLimits(100, 5, Duration.ofSeconds(30)));
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.TOO_MANY_COLUMNS);
    }

    @Test
    void exceedingTheTimeBudgetFailsTheWholeWorkbook() throws IOException {
        ParsedStatement r = parser.parse(new ByteArrayInputStream(workbook(rows(10))),
                new ParserLimits(100, 50, Duration.ZERO));
        assertThat(r.transactions()).isEmpty();
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.PARSE_TIMEOUT);
    }

    @Test
    void truncatedWorkbookIsUnreadable() throws IOException {
        byte[] full = workbook(rows(5));
        byte[] cut = Arrays.copyOf(full, full.length / 2);
        ParsedStatement r = parse(cut);
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.UNREADABLE_FILE);
    }
}
