package com.expenseanalyzer.processing.parser.pdf;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import com.expenseanalyzer.processing.parser.common.StatementRowMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Parses text-based PDF bank statements. A transaction is a line that starts with a date and ends
 * with the amount and the running balance; the balance moving up or down gives credit or debit.
 * Caller owns the stream. Expects an unencrypted PDF (uploads are unlocked first). Never logs text.
 */
@Component
public class PdfStatementParser {

    private static final String DATE = "\\d{2}[-/]\\d{2}[-/]\\d{4}|\\d{2}-[A-Za-z]{3}-\\d{4}";
    private static final String AMOUNT = "\\d[\\d,]*\\.\\d{2}";
    // date [value date] description amount balance [Cr|Dr]
    private static final Pattern TXN = Pattern.compile("^(" + DATE + ")\\s+(?:(?:" + DATE + ")\\s+)?(.+?)\\s+("
            + AMOUNT + ")\\s+(" + AMOUNT + ")(?:\\s*(?i:cr|dr))?$");
    private static final Pattern OPENING = Pattern.compile("(?i)opening balance\\D*(" + AMOUNT + ")");
    // ~50k rows at 50 per page; bounds work on page-count bombs
    private static final int MAX_PAGES = 1000;
    private static final StatementRowMapper MAPPER =
            new StatementRowMapper(List.of("date", "description", "debit", "credit"));

    public ParsedStatement parse(InputStream in) {
        return parse(in, ParserLimits.DEFAULTS);
    }

    public ParsedStatement parse(InputStream in, ParserLimits limits) {
        // Temp-file stream cache: decoded streams go to disk, not the heap.
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(in), IOUtils.createTempFileOnlyStreamCache())) {
            if (doc.getNumberOfPages() > MAX_PAGES) {
                return ParsedStatement.failed(new RowError(0, RowErrorCode.TOO_MANY_ROWS, null));
            }
            ParserLimits.Budget budget = limits.new Budget();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> text = new ArrayList<>();
            for (int page = 1; page <= doc.getNumberOfPages(); page++) { // page by page so the timeout applies
                budget.checkTime();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                text.addAll(List.of(stripper.getText(doc).split("\\R")));
            }
            String[] lines = text.toArray(String[]::new);

            List<ParsedTransaction> txns = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            BigDecimal previous = null;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                Matcher t = TXN.matcher(line);
                if (!t.matches()) {
                    Matcher o = OPENING.matcher(line);
                    if (previous == null && o.find()) {
                        previous = amount(o.group(1));
                    }
                    // ponytail: wrapped description lines are dropped; join them if banks need it
                    continue;
                }
                budget.checkRow(4);
                BigDecimal balance = amount(t.group(4));
                if (previous == null || balance.compareTo(previous) == 0) {
                    errors.add(new RowError(i + 1, RowErrorCode.AMBIGUOUS_DIRECTION, null));
                } else {
                    boolean credit = balance.compareTo(previous) > 0;
                    Object r = MAPPER.map(i + 1, List.of(t.group(1), t.group(2),
                            credit ? "" : t.group(3), credit ? t.group(3) : ""));
                    if (r instanceof ParsedTransaction p) {
                        txns.add(p);
                    } else {
                        errors.add((RowError) r);
                    }
                }
                previous = balance;
            }
            if (txns.isEmpty() && errors.isEmpty()) {
                // No text layer (scanned) or a layout without amount + balance columns.
                return ParsedStatement.failed(new RowError(0, RowErrorCode.UNREADABLE_FILE, null));
            }
            return new ParsedStatement(txns, errors);
        } catch (ParserLimits.LimitExceeded e) {
            return ParsedStatement.failed(new RowError(0, e.code(), null));
        } catch (Exception e) { // corrupt or encrypted input
            return ParsedStatement.failed(new RowError(0, RowErrorCode.UNREADABLE_FILE, null));
        }
    }

    private static BigDecimal amount(String s) {
        return new BigDecimal(s.replace(",", ""));
    }
}
