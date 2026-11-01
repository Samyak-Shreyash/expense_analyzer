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
 * Also handles layouts where the date sits on its own line and the description wraps until a line
 * ending in amount + balance, and layouts where a wrapped row's first lines come before the line with
 * date + amounts (Axis Bank as PDFBox reads it). A trailing branch-code column is ignored.
 * Caller owns the stream. Expects an unencrypted PDF (uploads are unlocked first). Never logs text.
 */
@Component
public class PdfStatementParser {

    private static final String DATE = "\\d{2}[-/]\\d{2}[-/]\\d{4}|\\d{2}-[A-Za-z]{3}-\\d{4}";
    private static final String AMOUNT = "\\d[\\d,]*\\.\\d{2}";
    private static final String END = "(" + AMOUNT + ")\\s+(" + AMOUNT + ")(?:\\s*(?i:cr|dr))?(?:\\s+\\d{1,6})?$";
    // date [value date] description amount balance [Cr|Dr] [branch code]
    private static final Pattern TXN = Pattern.compile("^(" + DATE + ")\\s+(?:(?:" + DATE + ")\\s+)?(.+?)\\s+" + END);
    private static final Pattern DATE_ONLY = Pattern.compile("^(" + DATE + ")$");
    // last line of a multi-line row: [description tail] amount balance [Cr|Dr] [branch code]
    private static final Pattern ROW_END = Pattern.compile("^(.*?)\\s*" + END);
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
            String pendingDate = null; // set while inside a multi-line row
            StringBuilder pendingDesc = new StringBuilder();
            int pendingLine = 0;
            StringBuilder lead = new StringBuilder(); // undated lines since the last row: the next row's first lines
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                Matcher t = TXN.matcher(line);
                if (t.matches()) {
                    pendingDate = null;
                    appendWrapped(lead, t.group(2));
                    budget.checkRow(4);
                    previous = row(i + 1, t.group(1), lead.toString(), t.group(3), t.group(4), previous, txns, errors);
                    lead.setLength(0);
                    continue;
                }
                Matcher d = DATE_ONLY.matcher(line);
                if (d.matches()) {
                    pendingDate = d.group(1);
                    pendingDesc.setLength(0);
                    lead.setLength(0);
                    pendingLine = i + 1;
                    continue;
                }
                if (pendingDate != null) {
                    Matcher e = ROW_END.matcher(line);
                    if (e.matches()) {
                        appendWrapped(pendingDesc, e.group(1));
                        budget.checkRow(4);
                        previous = row(pendingLine, pendingDate, pendingDesc.toString().trim(), e.group(2), e.group(3),
                                previous, txns, errors);
                        pendingDate = null;
                    } else if (!line.isEmpty()) {
                        appendWrapped(pendingDesc, line);
                    }
                    continue;
                }
                Matcher o = OPENING.matcher(line);
                if (previous == null && o.find()) {
                    previous = amount(o.group(1));
                    lead.setLength(0); // the account details and column header above it are not a description
                } else {
                    appendWrapped(lead, line);
                }
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

    /** Adds one row and returns its balance, the new "previous". */
    private static BigDecimal row(int lineNo, String date, String description, String amount, String balanceText,
            BigDecimal previous, List<ParsedTransaction> txns, List<RowError> errors) {
        BigDecimal balance = amount(balanceText);
        if (previous == null || balance.compareTo(previous) == 0) {
            errors.add(new RowError(lineNo, RowErrorCode.AMBIGUOUS_DIRECTION, null));
        } else {
            boolean credit = balance.compareTo(previous) > 0;
            Object r = MAPPER.map(lineNo, List.of(date, description, credit ? "" : amount, credit ? amount : ""));
            if (r instanceof ParsedTransaction p) {
                txns.add(p);
            } else {
                errors.add((RowError) r);
            }
        }
        return balance;
    }

    /** Joins a wrapped description line; no space next to a / | - delimiter, so "SAURAV" + "/UPI" stays "SAURAV/UPI". */
    private static void appendWrapped(StringBuilder desc, String part) {
        if (part.isEmpty()) {
            return;
        }
        boolean glued = desc.isEmpty() || "/|-".indexOf(desc.charAt(desc.length() - 1)) >= 0
                || "/|-".indexOf(part.charAt(0)) >= 0;
        desc.append(glued ? "" : " ").append(part);
    }

    private static BigDecimal amount(String s) {
        return new BigDecimal(s.replace(",", ""));
    }
}
