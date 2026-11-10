package com.expenseanalyzer.processing.parser.pdf;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GPayStatementParserTest {

    private final GPayStatementParser parser = new GPayStatementParser();

    private ParsedStatement parse(String... lines) throws IOException {
        return parser.parse(new ByteArrayInputStream(GPayPdf.of(lines)), ParserLimits.DEFAULTS);
    }

    private static String[] statement(String[]... rows) {
        List<String> lines = new ArrayList<>(List.of(
                "Transaction statement",
                "9999999999, someone@example.com",
                "Transaction statement period Sent Received",
                "01 May 2026 - 31 October 2026 1,10,372.50 7,300",
                "Date & time Transaction details Amount"));
        Stream.of(rows).forEach(r -> lines.addAll(List.of(r)));
        lines.add("Note: This statement reflects payments made by you on the Google Pay app.");
        lines.add("Page 1 of 1");
        return lines.toArray(String[]::new);
    }

    @Test
    void paidToIsDebitReceivedFromIsCreditAndSelfTransferIsDebit() throws IOException {
        ParsedStatement r = parse(statement(
                GPayPdf.row("01 May, 2026", "Paid to Hong's Kitchen", "372.50", "07:30 PM", "612100730574",
                        "Paid by Axis Bank 6457"),
                GPayPdf.row("03 May, 2026", "Received from Asha Rao", "7,300", "12:05 PM", "552481735563",
                        "Paid to Axis Bank 6457"),
                GPayPdf.row("14 Jun, 2026", "Self transfer to DBS Bank 5460", "1,10,000", "09:00 AM", "612399887766",
                        "Paid by Axis Bank 6457")));

        assertThat(r.errors()).isEmpty();
        List<ParsedTransaction> t = r.transactions();
        assertThat(t).extracting(ParsedTransaction::type).containsExactly("DEBIT", "CREDIT", "DEBIT");
        assertThat(t).extracting(p -> p.amount().toPlainString()).containsExactly("372.50", "7300", "110000");
        assertThat(t).extracting(ParsedTransaction::date).containsExactly(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), LocalDate.of(2026, 6, 14));
        assertThat(t.getFirst().rawDescription())
                .isEqualTo("Paid to Hong's Kitchen | UPI Transaction ID: 612100730574 | Paid by Axis Bank 6457");
        assertThat(t.get(2).rawDescription()).startsWith(GPayStatementParser.SELF_TRANSFER);
    }

    @Test
    void paymentMethodSuffixIsKept() throws IOException {
        ParsedStatement r = parse(statement(GPayPdf.row("04 May, 2026", "Paid to Blinkit", "1,000", "04:25 PM",
                "612148248644", "Paid by HDFC Bank 1234 | Paid via RuPay credit card")));

        assertThat(r.transactions()).singleElement().extracting(ParsedTransaction::rawDescription)
                .isEqualTo("Paid to Blinkit | UPI Transaction ID: 612148248644 | Paid by HDFC Bank 1234 | Paid via RuPay credit card");
    }

    @Test
    void headerAndFooterLinesAreNotRows() throws IOException {
        ParsedStatement r = parse(statement(GPayPdf.row("01 May, 2026", "Paid to CRED", "285", "01:18 PM",
                "612147423475", "Paid by DBS Bank 5460")));

        assertThat(r.transactions()).hasSize(1);
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void bankStatementPdfIsUnreadableAsGooglePay() throws IOException {
        ParsedStatement r = parse("01/05/2026 UPI/DR/612147423475/CRED 285.00 9,715.00");

        assertThat(r.transactions()).isEmpty();
        assertThat(r.errors()).singleElement().extracting(e -> e.code()).isEqualTo(RowErrorCode.UNREADABLE_FILE);
    }
}
