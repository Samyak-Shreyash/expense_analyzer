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
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
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
 * Parses a credit card statement PDF from any issuer. A transaction is one line as PDFBox reads it:
 * {@code <date> [time] <description> [₹|Rs.] <amount> [Cr|Dr|C|D]}, where the date is {@code dd/MM/uuuu},
 * {@code dd-MM-yy}, {@code dd MMM uuuu}, {@code dd-MMM-yy} or similar. The last amount on the line is the
 * transaction amount, so reward-point columns before it are kept in the description. A {@code Cr}/{@code C}
 * suffix or a leading {@code +} or {@code -} marks a credit (payment, refund, cashback); anything else is a
 * charge (DEBIT).
 * Caller owns the stream. Expects an unencrypted PDF (uploads are unlocked first). Never logs text.
 */
@Component
public class CreditCardStatementParser {

    private static final Pattern ROW = Pattern.compile(
            "^(\\d{1,2}[/\\-. ](?:\\d{1,2}|[A-Za-z]{3})[/\\-. ,]+\\d{2,4})"
                    + "(?:\\s*\\|?\\s*\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[AaPp][Mm])?)?"
                    + "\\s+(.+?)\\s+([+-])?\\s*(?:₹|Rs\\.?|INR)?\\s*([\\d,]+\\.\\d{2})\\s*(Cr|CR|cr|Dr|DR|dr|C|D)?$");
    private static final Pattern BILL_PAYMENT = Pattern.compile(
            "(?i)(PAYMENT\\s+(RECEIVED|THANK)|THANK\\s+YOU|BBPS|AUTO\\s*-?\\s*(PAY|DEBIT)|CC\\s+PAYMENT|PAYMENT\\s+-?\\s*(NEFT|IMPS|UPI)|NETBANKING\\s+PAYMENT)");
    private static final DateTimeFormatter DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("[d/M/uuuu][d/M/uu][d-M-uuuu][d-M-uu][d.M.uuuu][d.M.uu]"
                    + "[d MMM uuuu][d MMM uu][d-MMM-uuuu][d-MMM-uu][d MMM, uuuu][d/MMM/uuuu][d/MMM/uu]")
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int MAX_PAGES = 1000;

    /** True for the user's own bill payment to the card: a transfer, never income. */
    public static boolean isBillPayment(String description) {
        return BILL_PAYMENT.matcher(description).find();
    }

    public ParsedStatement parse(InputStream in, ParserLimits limits) {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(in), IOUtils.createTempFileOnlyStreamCache())) {
            if (doc.getNumberOfPages() > MAX_PAGES) {
                return ParsedStatement.failed(new RowError(0, RowErrorCode.TOO_MANY_ROWS, null));
            }
            ParserLimits.Budget budget = limits.new Budget();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<ParsedTransaction> txns = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            int lineNo = 0;
            for (int page = 1; page <= doc.getNumberOfPages(); page++) { // page by page so the timeout applies
                budget.checkTime();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                for (String raw : stripper.getText(doc).split("\\R")) {
                    lineNo++;
                    Matcher m = ROW.matcher(raw.trim());
                    if (m.matches()) {
                        budget.checkRow(4);
                        add(lineNo, m, txns, errors);
                    }
                }
            }
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

    private static void add(int line, Matcher m, List<ParsedTransaction> txns, List<RowError> errors) {
        LocalDate date;
        try {
            date = LocalDate.parse(m.group(1).replaceAll("\\s+", " "), DATE);
        } catch (RuntimeException e) {
            errors.add(new RowError(line, RowErrorCode.INVALID_DATE, "date"));
            return;
        }
        BigDecimal amount = new BigDecimal(m.group(4).replace(",", ""));
        if (amount.signum() <= 0) {
            errors.add(new RowError(line, RowErrorCode.INVALID_AMOUNT, "amount"));
            return;
        }
        String suffix = m.group(5);
        boolean credit = m.group(3) != null || (suffix != null && suffix.toUpperCase(Locale.ROOT).startsWith("C"));
        txns.add(new ParsedTransaction(line, date, m.group(2).trim(), amount, credit ? "CREDIT" : "DEBIT"));
    }
}
