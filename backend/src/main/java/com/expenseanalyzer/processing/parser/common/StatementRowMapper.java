package com.expenseanalyzer.processing.parser.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Maps one row of cells to a ParsedTransaction or a RowError, by header name. */
public class StatementRowMapper {

    private static final int MAX_DESCRIPTION = 2048;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT));

    private final int dateIdx;
    private final int descIdx;
    private final int debitIdx;
    private final int creditIdx;
    private final int amountIdx;

    public StatementRowMapper(List<String> header) {
        List<String> h = new ArrayList<>();
        for (String s : header) {
            h.add(s == null ? "" : s.trim().toLowerCase(Locale.ROOT));
        }
        dateIdx = h.indexOf("date");
        descIdx = h.indexOf("description");
        debitIdx = h.indexOf("debit");
        creditIdx = h.indexOf("credit");
        amountIdx = h.indexOf("amount");
    }

    public boolean hasValidHeaders() {
        return dateIdx >= 0 && descIdx >= 0 && (debitIdx >= 0 || creditIdx >= 0 || amountIdx >= 0);
    }

    /** Returns a ParsedTransaction or a RowError. */
    public Object map(int rowNumber, List<String> cells) {
        LocalDate date = parseDate(cell(cells, dateIdx));
        if (date == null) {
            return new RowError(rowNumber, RowErrorCode.INVALID_DATE, "Date");
        }
        String desc = cell(cells, descIdx);
        if (desc.isEmpty()) {
            return new RowError(rowNumber, RowErrorCode.MISSING_DESCRIPTION, "Description");
        }
        if (desc.length() > MAX_DESCRIPTION) {
            return new RowError(rowNumber, RowErrorCode.DESCRIPTION_TOO_LONG, "Description");
        }

        String raw;
        String type;
        String field;
        if (debitIdx >= 0 || creditIdx >= 0) {
            String debit = cell(cells, debitIdx);
            String credit = cell(cells, creditIdx);
            if (debit.isEmpty() == credit.isEmpty()) {
                return new RowError(rowNumber, RowErrorCode.AMBIGUOUS_DIRECTION, null);
            }
            type = debit.isEmpty() ? "CREDIT" : "DEBIT";
            raw = debit.isEmpty() ? credit : debit;
            field = debit.isEmpty() ? "Credit" : "Debit";
        } else {
            raw = cell(cells, amountIdx);
            type = null;
            field = "Amount";
        }

        BigDecimal value = parseAmount(raw);
        if (value == null || value.signum() == 0) {
            return new RowError(rowNumber, RowErrorCode.INVALID_AMOUNT, field);
        }
        if (type == null) {
            type = value.signum() < 0 ? "DEBIT" : "CREDIT";
        }
        return new ParsedTransaction(rowNumber, date, desc, value.abs(), type);
    }

    private static String cell(List<String> cells, int idx) {
        if (idx < 0 || idx >= cells.size() || cells.get(idx) == null) {
            return "";
        }
        return cells.get(idx).trim();
    }

    private static LocalDate parseDate(String s) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (RuntimeException ignored) {
                // try next format
            }
        }
        return null;
    }

    private static BigDecimal parseAmount(String s) {
        try {
            return new BigDecimal(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
