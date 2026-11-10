package com.expenseanalyzer.processing.parser.pdf;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Parses the Google Pay "Transaction statement" PDF. Each row is three lines as PDFBox reads them:
 * {@code 01 May, 2026 Paid to <payee> ₹1,234.50}, {@code 01:18 PM UPI Transaction ID: <12 digits>} and
 * {@code Paid by <bank> <last 4> [| ...]}. "Paid to" is a debit, "Received from" a credit, and "Self transfer to"
 * a debit between the user's own accounts. The description keeps all three lines joined by " | ".
 * Caller owns the stream. Expects an unencrypted PDF (uploads are unlocked first). Never logs text.
 */
@Component
public class GPayStatementParser {

    public static final String SELF_TRANSFER = "Self transfer to ";

    private static final Pattern ROW = Pattern.compile(
            "^(\\d{1,2} [A-Za-z]{3}, \\d{4})\\s+((?:Paid to|Received from|Self transfer to) .+?)\\s+₹?\\s?([\\d,]+(?:\\.\\d{1,2})?)$");
    private static final Pattern TIME = Pattern.compile("^\\d{1,2}:\\d{2} [AP]M\\s+(.*)$");
    private static final Pattern FOOTER = Pattern.compile("^(Note:|Page \\d+ of \\d+|received\\. Any payments)");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH);
    private static final int MAX_PAGES = 1000;

    public ParsedStatement parse(InputStream in, ParserLimits limits) {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(in), IOUtils.createTempFileOnlyStreamCache())) {
            if (doc.getNumberOfPages() > MAX_PAGES) {
                return ParsedStatement.failed(new RowError(0, RowErrorCode.TOO_MANY_ROWS, null));
            }
            ParserLimits.Budget budget = limits.new Budget();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> lines = new ArrayList<>();
            for (int page = 1; page <= doc.getNumberOfPages(); page++) { // page by page so the timeout applies
                budget.checkTime();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                lines.addAll(List.of(stripper.getText(doc).split("\\R")));
            }

            List<ParsedTransaction> txns = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            Row row = null;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                Matcher m = ROW.matcher(line);
                if (m.matches()) {
                    budget.checkRow(3);
                    add(row, txns, errors);
                    row = new Row(i + 1, m.group(1), m.group(2), m.group(3));
                } else if (row != null && row.details.size() < 3 && !line.isEmpty() && !FOOTER.matcher(line).find()) {
                    Matcher t = TIME.matcher(line);
                    row.details.add(t.matches() ? t.group(1) : line);
                }
            }
            add(row, txns, errors);
            if (txns.isEmpty() && errors.isEmpty()) {
                return ParsedStatement.failed(new RowError(0, RowErrorCode.UNREADABLE_FILE, null));
            }
            return new ParsedStatement(txns, errors);
        } catch (ParserLimits.LimitExceeded e) {
            return ParsedStatement.failed(new RowError(0, e.code(), null));
        } catch (Exception e) { // corrupt or encrypted input
            return ParsedStatement.failed(new RowError(0, RowErrorCode.UNREADABLE_FILE, null));
        }
    }

    private static void add(Row row, List<ParsedTransaction> txns, List<RowError> errors) {
        if (row == null) {
            return;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(row.date, DATE);
        } catch (RuntimeException e) {
            errors.add(new RowError(row.line, RowErrorCode.INVALID_DATE, "date"));
            return;
        }
        BigDecimal amount = new BigDecimal(row.amount.replace(",", ""));
        if (amount.signum() <= 0) {
            errors.add(new RowError(row.line, RowErrorCode.INVALID_AMOUNT, "amount"));
            return;
        }
        String type = row.details.getFirst().startsWith("Received from") ? "CREDIT" : "DEBIT";
        txns.add(new ParsedTransaction(row.line, date, String.join(" | ", row.details), amount, type));
    }

    /** One statement row while its lines are being collected; details starts with the payee line. */
    private static final class Row {
        final int line;
        final String date;
        final String amount;
        final List<String> details = new ArrayList<>();

        Row(int line, String date, String payee, String amount) {
            this.line = line;
            this.date = date;
            this.amount = amount;
            details.add(payee);
        }
    }
}
