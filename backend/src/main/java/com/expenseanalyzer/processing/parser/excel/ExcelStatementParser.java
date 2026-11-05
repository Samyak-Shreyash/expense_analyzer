package com.expenseanalyzer.processing.parser.excel;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import com.expenseanalyzer.processing.parser.common.StatementRowMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/** Parses the first sheet of an XLS or XLSX bank statement. Caller owns the stream. Never logs cell values. */
@Component
public class ExcelStatementParser {

    // POI loads the XLSX XML into memory, so bound it before opening. Roomy for 50k rows of a normal statement.
    private static final long MAX_INFLATED_BYTES = 100L * 1024 * 1024;

    public ParsedStatement parse(InputStream in) {
        return parse(in, ParserLimits.DEFAULTS);
    }

    public ParsedStatement parse(InputStream in, ParserLimits limits) {
        byte[] file;
        try {
            file = in.readAllBytes();
            if (inflatesPastCap(file)) {
                return ParsedStatement.failed(new RowError(0, RowErrorCode.TOO_MANY_ROWS, null));
            }
        } catch (IOException e) { // corrupt zip; the size check does not apply to XLS, which is not a zip
            return ParsedStatement.failed(new RowError(0, RowErrorCode.UNREADABLE_FILE, null));
        }
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            Sheet sheet = wb.getSheetAt(0);
            StatementRowMapper mapper = null;
            List<ParsedTransaction> txns = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            ParserLimits.Budget budget = limits.new Budget();
            for (Row row : sheet) {
                if (mapper != null) {
                    budget.checkRow(Math.max(row.getLastCellNum(), 0)); // before materialising cells
                } else {
                    budget.checkPreambleRow(Math.max(row.getLastCellNum(), 0));
                }
                List<String> cells = cells(row);
                if (cells.stream().allMatch(String::isEmpty)) {
                    continue;
                }
                if (mapper != null && cells.stream().allMatch(c -> c.isEmpty() || c.matches("\\*+"))) {
                    // HDFC rules "****" rows under the header and after the last transaction; a summary follows.
                    if (txns.isEmpty() && errors.isEmpty()) {
                        continue;
                    }
                    break;
                }
                if (mapper == null) {
                    // Bank exports put account details above the header; skip rows until it appears.
                    StatementRowMapper candidate = new StatementRowMapper(cells);
                    mapper = candidate.hasValidHeaders() ? candidate : null;
                    continue;
                }
                Object r = mapper.map(row.getRowNum() + 1, cells);
                if (r instanceof ParsedTransaction t) {
                    txns.add(t);
                } else {
                    errors.add((RowError) r);
                }
            }
            if (mapper == null) {
                return ParsedStatement.failed(new RowError(1, RowErrorCode.MISSING_HEADER, null));
            }
            return new ParsedStatement(txns, errors);
        } catch (ParserLimits.LimitExceeded e) {
            return ParsedStatement.failed(new RowError(0, e.code(), null));
        } catch (Exception e) { // corrupt/non-Excel input: POI throws assorted runtime exceptions
            return ParsedStatement.failed(new RowError(0, RowErrorCode.UNREADABLE_FILE, null));
        }
    }

    /** Counts bytes actually inflated, stopping at the cap, rather than trusting sizes the zip declares. */
    private static boolean inflatesPastCap(byte[] file) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file))) {
            while (zip.getNextEntry() != null) {
                int n;
                while ((n = zip.read(buffer)) > 0) {
                    total += n;
                    if (total > MAX_INFLATED_BYTES) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<String> cells(Row row) {
        int n = Math.max(row.getLastCellNum(), 0);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Cell c = row.getCell(i);
            out.add(c == null ? "" : text(c, c.getCellType()));
        }
        return out;
    }

    private static String text(Cell c, CellType type) {
        return switch (type) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(c)
                    ? c.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case FORMULA -> text(c, c.getCachedFormulaResultType());
            default -> "";
        };
    }
}
