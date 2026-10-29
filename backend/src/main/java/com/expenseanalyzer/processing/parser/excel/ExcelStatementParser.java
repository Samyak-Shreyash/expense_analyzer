package com.expenseanalyzer.processing.parser.excel;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import com.expenseanalyzer.processing.parser.common.StatementRowMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Parses the first sheet of an XLSX bank statement. Caller owns the stream. Never logs cell values. */
@Component
public class ExcelStatementParser {

    public ParsedStatement parse(InputStream in) {
        return parse(in, ParserLimits.DEFAULTS);
    }

    public ParsedStatement parse(InputStream in, ParserLimits limits) {
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            StatementRowMapper mapper = null;
            List<ParsedTransaction> txns = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            ParserLimits.Budget budget = limits.new Budget();
            for (Row row : sheet) {
                if (mapper != null) {
                    budget.checkRow(Math.max(row.getLastCellNum(), 0)); // data rows only, before materialising cells
                }
                List<String> cells = cells(row);
                if (cells.stream().allMatch(String::isEmpty)) {
                    continue;
                }
                if (mapper == null) {
                    mapper = new StatementRowMapper(cells);
                    if (!mapper.hasValidHeaders()) {
                        return ParsedStatement.failed(new RowError(row.getRowNum() + 1, RowErrorCode.MISSING_HEADER, null));
                    }
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
        } catch (Exception e) { // corrupt/non-xlsx input: POI throws assorted runtime exceptions
            return ParsedStatement.failed(new RowError(0, RowErrorCode.UNREADABLE_FILE, null));
        }
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
